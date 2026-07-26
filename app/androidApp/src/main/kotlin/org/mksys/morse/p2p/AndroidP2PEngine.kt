package org.mksys.morse.p2p

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mksys.morse.core.model.IdentityRecord
import org.mksys.morse.core.p2p.P2PEvent
import org.mksys.morse.core.p2p.P2PEngine
import org.mksys.morse.database.AndroidDatabaseManager
import org.mksys.morse.tor.AndroidTorManager
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class AndroidP2PEngine(
    private val databaseManager: AndroidDatabaseManager,
    private val torManager: AndroidTorManager
) : P2PEngine {

    companion object {
        private const val TAG = "AndroidP2PEngine"
        const val ACK_MIME_TYPE = "application/x-morse-ack"
        const val INVITE_MIME_TYPE = "application/x-morse-invite"
        const val INVITE_ACCEPT_MIME_TYPE = "application/x-morse-invite-accept"
        const val DISCONNECT_MIME_TYPE = "application/x-morse-disconnect"
        const val IDENTITY_UPDATE_MIME_TYPE = "application/x-morse-identity-update"
        const val IDLE_TIMEOUT_MS = 120_000L
        const val HS_VIRTUAL_PORT = 9995
        const val MAX_MESSAGE_SIZE = 20 * 1024 * 1024
    }

    private val _events = MutableSharedFlow<P2PEvent>(replay = 0, extraBufferCapacity = 64)
    override val events: SharedFlow<P2PEvent> = _events

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private var serverJob: Job? = null
    private var keepaliveJob: Job? = null
    private var healthCheckJob: Job? = null

    private val connectionPool = ConcurrentHashMap<String, PooledConnection>()
    private val conversationActive = ConcurrentHashMap<String, Boolean>()
    private val idleTimers = ConcurrentHashMap<String, Job>()

    private data class PooledConnection(
        val socket: Socket,
        var lastActivity: Long = System.currentTimeMillis()
    )

    override fun start() {
        if (isRunning.getAndSet(true)) return

        val port = torManager.getLocalPort().takeIf { it > 0 } ?: torManager.startLocalServer(torManager.getPort())

        serverJob = scope.launch {
            try {
                listenForIncoming()
            } catch (e: Exception) {
                Log.e(TAG, "Erro crítico em listenForIncoming", e)
            }
        }
        keepaliveJob = scope.launch {
            try {
                keepaliveLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Erro crítico em keepaliveLoop", e)
            }
        }
        healthCheckJob = scope.launch {
            try {
                healthCheckLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Erro crítico em healthCheckLoop", e)
            }
        }
    }

    override fun stop() {
        isRunning.set(false)
        serverJob?.cancel()
        serverJob = null
        keepaliveJob?.cancel()
        keepaliveJob = null
        healthCheckJob?.cancel()
        healthCheckJob = null

        connectionPool.values.forEach { pooled ->
            try { pooled.socket.close() } catch (_: Exception) {}
        }
        connectionPool.clear()
        conversationActive.clear()
        idleTimers.values.forEach { it.cancel() }
        idleTimers.clear()

        torManager.stopLocalServer()
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override suspend fun sendMessage(
        recipientPublicKeyB64: String,
        recipientX25519PublicKeyB64: String,
        recipientOnionAddress: String,
        content: String,
        mimeType: String,
        messageId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val keys = databaseManager.getIdentity() ?: return@withContext false

            val theirXPubBytes = CryptoUtils.decodeKey(recipientX25519PublicKeyB64)
            if (theirXPubBytes.size != 32) return@withContext false

            val myDisplayName = databaseManager.getIdentity()?.displayName ?: ""

            val wireMsg = WireMessage(
                senderPublicKey = keys.ed25519PublicKey,
                senderX25519PublicKey = keys.x25519PublicKey,
                senderOnionAddress = torManager.getOnionAddress() ?: "",
                timestamp = System.currentTimeMillis(),
                senderDisplayName = myDisplayName,
                content = content,
                mimeType = mimeType,
                messageId = messageId
            )

            val encryptedData = MessageProtocol.encrypt(wireMsg, theirXPubBytes)
            val signature = CryptoUtils.sign(keys.ed25519PrivateKey, encryptedData)
            val wireData = keys.ed25519PublicKey + encryptedData + signature

            val existingConn = connectionPool[recipientPublicKeyB64]
            if (existingConn != null && existingConn.socket.isConnected && !existingConn.socket.isClosed) {
                existingConn.lastActivity = System.currentTimeMillis()
                try {
                    MessageProtocol.writeMessage(existingConn.socket.getOutputStream(), wireData)
                    return@withContext true
                } catch (e: Exception) {
                    connectionPool.remove(recipientPublicKeyB64)
                    try { existingConn.socket.close() } catch (_: Exception) {}
                }
            }

            val socket = torManager.connectToOnion(recipientOnionAddress, HS_VIRTUAL_PORT)
            socket.soTimeout = 60000
            try {
                MessageProtocol.writeMessage(socket.getOutputStream(), wireData)

                if (conversationActive[recipientPublicKeyB64] == true) {
                    connectionPool[recipientPublicKeyB64] = PooledConnection(socket)
                    startReaderForPooledConnection(recipientPublicKeyB64, socket)
                } else {
                    socket.close()
                }
                true
            } catch (e: Exception) {
                try { socket.close() } catch (_: Exception) {}
                throw e
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendMessage falhou: ${e.message}")
            false
        }
    }

    override suspend fun sendConnectionRequest(
        targetOnion: String,
        targetX25519PublicKeyB64: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val keys = databaseManager.getIdentity() ?: return@withContext false
            val theirXPubBytes = CryptoUtils.decodeKey(targetX25519PublicKeyB64)

            val inviteMsg = WireMessage(
                senderPublicKey = keys.ed25519PublicKey,
                senderX25519PublicKey = keys.x25519PublicKey,
                senderOnionAddress = torManager.getOnionAddress() ?: "",
                timestamp = System.currentTimeMillis(),
                senderDisplayName = keys.displayName,
                content = "CONN_REQ",
                mimeType = INVITE_MIME_TYPE
            )

            val encryptedData = MessageProtocol.encrypt(inviteMsg, theirXPubBytes)
            val signature = CryptoUtils.sign(keys.ed25519PrivateKey, encryptedData)
            val wireData = keys.ed25519PublicKey + encryptedData + signature

            val socket = torManager.connectToOnion(targetOnion, HS_VIRTUAL_PORT)
            socket.use { sock ->
                sock.soTimeout = 30000
                MessageProtocol.writeMessage(sock.getOutputStream(), wireData)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "sendConnectionRequest falhou: ${e.message}")
            false
        }
    }

    override suspend fun sendDisconnect(
        targetOnion: String,
        targetX25519PublicKeyB64: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val keys = databaseManager.getIdentity() ?: return@withContext false
            val theirXPubBytes = CryptoUtils.decodeKey(targetX25519PublicKeyB64)

            val discMsg = WireMessage(
                senderPublicKey = keys.ed25519PublicKey,
                senderX25519PublicKey = keys.x25519PublicKey,
                senderOnionAddress = torManager.getOnionAddress() ?: "",
                timestamp = System.currentTimeMillis(),
                senderDisplayName = keys.displayName,
                content = "DISCONNECT",
                mimeType = DISCONNECT_MIME_TYPE
            )

            val encryptedData = MessageProtocol.encrypt(discMsg, theirXPubBytes)
            val signature = CryptoUtils.sign(keys.ed25519PrivateKey, encryptedData)
            val wireData = keys.ed25519PublicKey + encryptedData + signature

            val socket = torManager.connectToOnion(targetOnion, HS_VIRTUAL_PORT)
            socket.use { sock ->
                sock.soTimeout = 15000
                MessageProtocol.writeMessage(sock.getOutputStream(), wireData)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "sendDisconnect falhou: ${e.message}")
            false
        }
    }

    override suspend fun acceptConnectionRequest(
        theirPublicKeyB64: String,
        theirOnionAddress: String,
        theirX25519PubB64: String,
        displayName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val keys = databaseManager.getIdentity() ?: return@withContext false
            val theirXPubBytes = CryptoUtils.decodeKey(theirX25519PubB64)

            val acceptMsg = WireMessage(
                senderPublicKey = keys.ed25519PublicKey,
                senderX25519PublicKey = keys.x25519PublicKey,
                senderOnionAddress = torManager.getOnionAddress() ?: "",
                timestamp = System.currentTimeMillis(),
                senderDisplayName = keys.displayName,
                content = "CONN_ACCEPT",
                mimeType = INVITE_ACCEPT_MIME_TYPE
            )

            val encryptedData = MessageProtocol.encrypt(acceptMsg, theirXPubBytes)
            val signature = CryptoUtils.sign(keys.ed25519PrivateKey, encryptedData)
            val wireData = keys.ed25519PublicKey + encryptedData + signature

            val socket = torManager.connectToOnion(theirOnionAddress, HS_VIRTUAL_PORT)
            socket.use { sock ->
                sock.soTimeout = 30000
                MessageProtocol.writeMessage(sock.getOutputStream(), wireData)
            }

            databaseManager.insertContact(
                publicKey = theirPublicKeyB64,
                x25519PublicKey = theirX25519PubB64,
                onionAddress = theirOnionAddress,
                displayName = displayName.ifBlank { theirPublicKeyB64.take(16) },
                createdAt = System.currentTimeMillis()
            )

            databaseManager.insertAppEvent(
                type = "contact_accepted",
                senderPublicKey = theirPublicKeyB64,
                senderDisplayName = displayName,
                message = "Contato aceito: $displayName",
                timestamp = System.currentTimeMillis()
            )

            databaseManager.deleteConnectionRequest(theirPublicKeyB64)

            true
        } catch (e: Exception) {
            Log.w(TAG, "acceptConnectionRequest falhou: ${e.message}")
            false
        }
    }

    override fun setConversationActive(contactPublicKeyB64: String, active: Boolean) {
        conversationActive[contactPublicKeyB64] = active
        idleTimers[contactPublicKeyB64]?.cancel()
        idleTimers.remove(contactPublicKeyB64)

        if (!active) {
            val timer = scope.launch {
                delay(IDLE_TIMEOUT_MS)
                val pooled = connectionPool.remove(contactPublicKeyB64)
                if (pooled != null) {
                    try { pooled.socket.close() } catch (_: Exception) {}
                    Log.i(TAG, "Conexão idle fechada para $contactPublicKeyB64")
                }
                idleTimers.remove(contactPublicKeyB64)
            }
            idleTimers[contactPublicKeyB64] = timer
        }
    }

    private fun startReaderForPooledConnection(contactPubKeyB64: String, socket: Socket) {
        scope.launch {
            try {
                while (isRunning.get() && socket.isConnected && !socket.isClosed) {
                    socket.soTimeout = 60000
                    val rawData = MessageProtocol.readMessage(socket.getInputStream()) ?: break
                    handleIncomingData(rawData)
                    connectionPool[contactPubKeyB64]?.lastActivity = System.currentTimeMillis()
                }
            } catch (_: Exception) {
            } finally {
                connectionPool.remove(contactPubKeyB64)
            }
        }
    }

    private suspend fun listenForIncoming() {
        while (isRunning.get()) {
            try {
                val socket = torManager.acceptConnection()
                if (socket == null) {
                    if (!isRunning.get()) break
                    if (torManager.getLocalPort() <= 0) {
                        torManager.restartLocalServer()
                    }
                    delay(500)
                    continue
                }
                scope.launch {
                    try {
                        socket.soTimeout = 60000
                        val rawData = MessageProtocol.readMessage(socket.getInputStream())
                        if (rawData != null) {
                            handleIncomingData(rawData)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Falha ao processar conexão: ${e.message}")
                    } finally {
                        try { socket.close() } catch (_: Exception) {}
                    }
                }
            } catch (_: CancellationException) {
                break
            } catch (e: Exception) {
                Log.w(TAG, "Erro em listenForIncoming: ${e.message}")
                if (isRunning.get()) {
                    torManager.restartLocalServer()
                    delay(1000)
                }
            }
        }
    }

    private suspend fun handleIncomingData(rawData: ByteArray) {
        if (rawData.size < 141) {
            Log.w(TAG, "Mensagem recebida muito pequena: ${rawData.size} bytes")
            return
        }

        val senderEdPub = rawData.copyOfRange(0, 32)
        val signature = rawData.copyOfRange(rawData.size - 64, rawData.size)
        val encryptedData = rawData.copyOfRange(32, rawData.size - 64)

        if (!CryptoUtils.verify(senderEdPub, encryptedData, signature)) {
            Log.e(TAG, "Falha na verificação de assinatura")
            return
        }

        val keys = databaseManager.getIdentity() ?: return
        val wireMsg = MessageProtocol.decrypt(encryptedData, keys.x25519PrivateKey)

        val isIdentityUpdate = wireMsg.mimeType == IDENTITY_UPDATE_MIME_TYPE
        if (!isIdentityUpdate && !wireMsg.senderPublicKey.contentEquals(senderEdPub)) {
            Log.e(TAG, "Spoof: chave interna != chave externa")
            return
        }

        // TODO: Usuário quer aperfeiçoar a tolerância de relógio depois.
        // Atualmente usa 30 minutos para cada lado — aceitar ajuste futuro.
        val now = System.currentTimeMillis()
        val clockTolerance = 30 * 60 * 1000L
        if (wireMsg.timestamp < now - clockTolerance || wireMsg.timestamp > now + clockTolerance) {
            val diffMinutes = (wireMsg.timestamp - now) / 60_000
            Log.e(TAG, "Mensagem rejeitada: desvio de relógio ${diffMinutes} min")
            return
        }

        val senderEdB64 = CryptoUtils.encodeKey(senderEdPub)
        val senderXB64 = CryptoUtils.encodeKey(wireMsg.senderX25519PublicKey)

        val isTechnicalMessage = wireMsg.mimeType == ACK_MIME_TYPE ||
                wireMsg.mimeType == INVITE_MIME_TYPE ||
                wireMsg.mimeType == INVITE_ACCEPT_MIME_TYPE ||
                wireMsg.mimeType == DISCONNECT_MIME_TYPE ||
                wireMsg.mimeType == IDENTITY_UPDATE_MIME_TYPE

        if (wireMsg.mimeType == ACK_MIME_TYPE) {
            val parts = wireMsg.content.split("|")
            val msgId = parts.firstOrNull() ?: ""
            val deliveredTs = parts.getOrNull(1)?.toLongOrNull() ?: now
            if (msgId.isNotBlank()) {
                databaseManager.updateMessageStatusBySenderMessageId(msgId, "delivered", deliveredTs)
            }
            return
        }

        if (wireMsg.mimeType == INVITE_MIME_TYPE) {
            databaseManager.insertConnectionRequest(
                publicKey = senderEdB64,
                x25519PublicKey = senderXB64,
                onionAddress = wireMsg.senderOnionAddress,
                displayName = wireMsg.senderDisplayName,
                timestamp = wireMsg.timestamp
            )
            databaseManager.insertAppEvent(
                type = "connection_request",
                senderPublicKey = senderEdB64,
                senderDisplayName = wireMsg.senderDisplayName,
                message = "Pedido de conexão de ${wireMsg.senderDisplayName.ifBlank { senderEdB64.take(16) }}",
                timestamp = wireMsg.timestamp
            )
            _events.emit(P2PEvent.ConnectionRequest(
                senderPublicKeyB64 = senderEdB64,
                senderX25519PublicKeyB64 = senderXB64,
                senderOnionAddress = wireMsg.senderOnionAddress,
                senderDisplayName = wireMsg.senderDisplayName
            ))
            return
        }

        if (wireMsg.mimeType == INVITE_ACCEPT_MIME_TYPE) {
            databaseManager.insertContact(
                publicKey = senderEdB64,
                x25519PublicKey = senderXB64,
                onionAddress = wireMsg.senderOnionAddress,
                displayName = wireMsg.senderDisplayName,
                createdAt = System.currentTimeMillis()
            )
            databaseManager.insertAppEvent(
                type = "contact_accepted",
                senderPublicKey = senderEdB64,
                senderDisplayName = wireMsg.senderDisplayName,
                message = "Contato aceitou: ${wireMsg.senderDisplayName.ifBlank { senderEdB64.take(16) }}",
                timestamp = wireMsg.timestamp
            )
            _events.emit(P2PEvent.ConnectionAccepted(
                senderPublicKeyB64 = senderEdB64,
                senderX25519PublicKeyB64 = senderXB64,
                senderOnionAddress = wireMsg.senderOnionAddress,
                senderDisplayName = wireMsg.senderDisplayName
            ))
            return
        }

        if (wireMsg.mimeType == DISCONNECT_MIME_TYPE) {
            databaseManager.setContactDisconnected(senderEdB64)
            connectionPool.remove(senderEdB64)
            databaseManager.insertAppEvent(
                type = "contact_disconnected",
                senderPublicKey = senderEdB64,
                senderDisplayName = null,
                message = "Contato desconectou: $senderEdB64",
                timestamp = wireMsg.timestamp
            )
            _events.emit(P2PEvent.TerminationReceived(senderPublicKeyB64 = senderEdB64))
            return
        }

        if (wireMsg.mimeType == IDENTITY_UPDATE_MIME_TYPE) {
            val newEdB64 = CryptoUtils.encodeKey(wireMsg.senderPublicKey)
            val newX25519B64 = CryptoUtils.encodeKey(wireMsg.senderX25519PublicKey)
            databaseManager.insertContact(
                publicKey = newEdB64,
                x25519PublicKey = newX25519B64,
                onionAddress = wireMsg.senderOnionAddress,
                displayName = wireMsg.senderDisplayName,
                createdAt = System.currentTimeMillis()
            )
            databaseManager.deleteContact(senderEdB64)
            return
        }

        val contact = databaseManager.getContactByPublicKey(senderEdB64)
        if (contact == null || contact.isDisconnected || contact.isBlocked) {
            Log.w(TAG, "Mensagem de remetente desconhecido/bloqueado: $senderEdB64")
            return
        }

        if (wireMsg.messageId.isNotBlank() && databaseManager.getMessageBySenderMessageId(wireMsg.messageId)) {
            Log.d(TAG, "Mensagem duplicada ignorada: ${wireMsg.messageId}")
            return
        }

        databaseManager.insertMessage(
            senderPublicKey = senderEdB64,
            senderX25519PublicKey = senderXB64,
            senderOnionAddress = wireMsg.senderOnionAddress,
            content = wireMsg.content,
            isOutgoing = false,
            status = "received",
            timestamp = wireMsg.timestamp,
            mimeType = wireMsg.mimeType,
            audioBytes = wireMsg.audioBytes.takeIf { it.isNotEmpty() },
            durationMs = null,
            senderMessageId = wireMsg.messageId.ifBlank { null },
            deliveryTimestamp = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )

        databaseManager.updateContactLastSeen(senderEdB64, System.currentTimeMillis())

        _events.emit(P2PEvent.MessageReceived(
            senderPublicKeyB64 = senderEdB64,
            timestamp = wireMsg.timestamp
        ))
    }

    private suspend fun keepaliveLoop() {
        while (isRunning.get()) {
            delay(5 * 60 * 1000L)
            try {
                if (torManager.getLocalPort() <= 0) {
                    Log.w(TAG, "Keepalive: servidor local caindo, reiniciando")
                    torManager.restartLocalServer()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro no keepalive: ${e.message}")
            }
        }
    }

    private suspend fun healthCheckLoop() {
        var localFailCount = 0
        while (isRunning.get()) {
            delay(15_000)
            try {
                if (torManager.getLocalPort() <= 0) {
                    localFailCount++
                    if (localFailCount >= 2) {
                        localFailCount = 0
                        Log.w(TAG, "Health check: servidor local caindo, reiniciando")
                        torManager.restartLocalServer()
                    }
                } else {
                    localFailCount = 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro no health check: ${e.message}")
            }
        }
    }
}
