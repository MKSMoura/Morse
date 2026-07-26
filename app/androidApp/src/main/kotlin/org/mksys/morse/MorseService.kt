package org.mksys.morse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.os.UserManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mksys.morse.core.model.IdentityStatus
import org.mksys.morse.core.p2p.P2PEvent
import org.mksys.morse.core.service.TorStatus
import org.mksys.morse.database.AndroidDatabaseManager
import org.mksys.morse.identity.AndroidIdentityManager
import org.mksys.morse.p2p.AndroidP2PEngine
import org.mksys.morse.tor.AndroidTorManager
import java.io.File

class MorseService : Service() {

    companion object {
        private const val TAG = "MorseService"
        const val CHANNEL_ID = "morse_service"
        const val NOTIFICATION_ID = 1
        const val TEMP_FILE_NAME = "tor_keys_temp.bin"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MorseService = this@MorseService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val statusFlow = MutableStateFlow<TorStatus>(TorStatus.Starting)
    private val identityStatusFlow = MutableStateFlow<IdentityStatus>(IdentityStatus.Unknown)

    private lateinit var identityManager: AndroidIdentityManager
    private lateinit var databaseManager: AndroidDatabaseManager
    private lateinit var torManager: AndroidTorManager
    private var p2pEngine: AndroidP2PEngine? = null

    private var autoRetryDone = false
    private var userActionDeferred: CompletableDeferred<Boolean>? = null

    private var pendingIdentityName: String = ""
    private var pendingIdentityPin: String = ""
    private var pendingIdentityOnion: String = ""
    private var pendingIdentityKeys: org.mksys.morse.core.model.IdentityKeys? = null

    private var userUnlockedReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Iniciando..."))

        identityManager = AndroidIdentityManager()
        databaseManager = AndroidDatabaseManager(applicationContext)
        torManager = AndroidTorManager(applicationContext)
        p2pEngine = AndroidP2PEngine(
            databaseManager = databaseManager,
            torManager = torManager
        )

        startTor()
        checkInitialIdentityStatus()
    }

    override fun onDestroy() {
        p2pEngine?.stop()
        unregisterUserUnlockedReceiver()
        scope.cancel()
        super.onDestroy()
    }

    private fun checkInitialIdentityStatus() {
        scope.launch {
            val userManager = getSystemService(Context.USER_SERVICE) as UserManager
            if (userManager.isUserUnlocked) {
                val status = databaseManager.hasRealIdentity()
                if (status is IdentityStatus.Absent) {
                    databaseManager.deleteDatabase()
                }
                identityStatusFlow.value = status
            } else {
                identityStatusFlow.value = IdentityStatus.Unknown
                registerUserUnlockedReceiver()
            }
        }
    }

    private fun registerUserUnlockedReceiver() {
        if (userUnlockedReceiver != null) return

        userUnlockedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                    Log.i(TAG, "Dispositivo desbloqueado, verificando identidade")
                    scope.launch {
                        val status = databaseManager.hasRealIdentity()
                        if (status is IdentityStatus.Absent) {
                            databaseManager.deleteDatabase()
                        }
                        identityStatusFlow.value = status
                    }
                    unregisterUserUnlockedReceiver()
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        registerReceiver(userUnlockedReceiver, filter)
        Log.i(TAG, "Receiver de desbloqueio registrado")
    }

    private fun unregisterUserUnlockedReceiver() {
        userUnlockedReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao desregistrar receiver (ignorado): ${e.message}")
            }
            userUnlockedReceiver = null
        }
    }

    private fun startTor() {
        scope.launch {
            statusFlow.value = TorStatus.Starting
            updateNotification("Iniciando Tor...")
            autoRetryDone = false
            connectTor()
        }
    }

    private suspend fun connectTor() {
        try {
            torManager.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao parar Tor (ignorado): ${e.message}")
        }

        delay(3000L)

        try {
            torManager.start()

            statusFlow.value = TorStatus.Connecting("Conectando à rede Tor...")
            updateNotification("Conectando à rede Tor...")

            torManager.bootstrapProgress.collect { progress ->
                val message = when {
                    progress <= 0  -> "Iniciando Tor..."
                    progress < 10  -> "Conectando... $progress%"
                    progress < 30  -> "Conectando à rede... $progress%"
                    progress < 50  -> "Carregando consenso... $progress%"
                    progress < 75  -> "Carregando descritores... $progress%"
                    progress < 95  -> "Estabelecendo circuitos... $progress%"
                    else           -> "Conectado via Tor"
                }

                statusFlow.value = TorStatus.Connecting(message)
                updateNotification(message)

                if (progress >= 100) {
                    val onionAddress = torManager.getOnionAddress()
                    saveTempFile(onionAddress ?: "")

                    statusFlow.value = TorStatus.Connected
                    updateNotification("Conectado via Tor")
                    Log.i(TAG, "Tor conectado: $onionAddress")
                    p2pEngine?.start()
                    return@collect
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro na conexão Tor: ${e.message}", e)
            handleError()
        }
    }

    private suspend fun handleError() {
        if (!autoRetryDone) {
            autoRetryDone = true
            statusFlow.value = TorStatus.Connecting("Falha na conexão. Tentando novamente em 15s...")
            updateNotification("Falha na conexão. Tentando novamente em 15s...")
            delay(15000L)
            connectTor()
        } else {
            statusFlow.value = TorStatus.NeedsAction("Falha na conexão")
            updateNotification("Falha na conexão")
            userActionDeferred = CompletableDeferred()
            val retry = userActionDeferred!!.await()
            userActionDeferred = null
            if (retry) {
                connectTor()
            } else {
                try {
                    torManager.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Erro ao parar Tor (ignorado): ${e.message}")
                }
                statusFlow.value = TorStatus.StoppedTrying
                updateNotification("Conexão interrompida")
            }
        }
    }

    fun confirmRetry() {
        userActionDeferred?.complete(true)
    }

    fun leaveForLater() {
        userActionDeferred?.complete(false)
    }

    fun observeStatus(): StateFlow<TorStatus> = statusFlow.asStateFlow()

    fun observeIdentity(): StateFlow<IdentityStatus> = identityStatusFlow.asStateFlow()

    fun observeP2PEvents(): SharedFlow<P2PEvent>? = p2pEngine?.events

    fun setConversationActive(contactPublicKeyB64: String, active: Boolean) {
        p2pEngine?.setConversationActive(contactPublicKeyB64, active)
    }

    suspend fun sendMessage(
        recipientPublicKeyB64: String,
        recipientX25519PublicKeyB64: String,
        recipientOnionAddress: String,
        content: String,
        mimeType: String = "",
        messageId: String = ""
    ): Boolean = p2pEngine?.sendMessage(
        recipientPublicKeyB64, recipientX25519PublicKeyB64, recipientOnionAddress, content, mimeType, messageId
    ) ?: false

    suspend fun sendConnectionRequest(targetOnion: String, targetX25519PublicKeyB64: String): Boolean =
        p2pEngine?.sendConnectionRequest(targetOnion, targetX25519PublicKeyB64) ?: false

    suspend fun acceptConnectionRequest(
        theirPublicKeyB64: String,
        theirOnionAddress: String,
        theirX25519PubB64: String,
        displayName: String = ""
    ): Boolean = p2pEngine?.acceptConnectionRequest(theirPublicKeyB64, theirOnionAddress, theirX25519PubB64, displayName) ?: false

    suspend fun sendDisconnect(targetOnion: String, targetX25519PublicKeyB64: String): Boolean =
        p2pEngine?.sendDisconnect(targetOnion, targetX25519PublicKeyB64) ?: false

    suspend fun deleteContact(publicKey: String) {
        databaseManager.deleteContact(publicKey)
        databaseManager.deleteMessagesBySender(publicKey)
    }

    suspend fun getContact(publicKey: String) = databaseManager.getContactByPublicKey(publicKey)

    suspend fun getAllContacts() = databaseManager.getAllContacts()

    suspend fun getMessagesBySender(senderPublicKey: String) = databaseManager.getMessagesBySender(senderPublicKey)

    suspend fun getLastMessagePerContact() = databaseManager.getLastMessagePerContact()

    suspend fun getConnectionRequests() = databaseManager.getConnectionRequests()

    suspend fun getUnreadEventCount() = databaseManager.getUnreadEventCount()

    suspend fun markAllAppEventsAsRead() = databaseManager.markAllAppEventsAsRead()

    fun retryConnection() {
        scope.launch {
            statusFlow.value = TorStatus.Connecting("Reiniciando Tor...")
            updateNotification("Reiniciando Tor...")

            try {
                torManager.stop()
                delay(3000L)

                torManager.start()

                torManager.bootstrapProgress.collect { progress ->
                    val message = when {
                        progress <= 0  -> "Iniciando Tor..."
                        progress < 10  -> "Conectando... $progress%"
                        progress < 30  -> "Conectando à rede... $progress%"
                        progress < 50  -> "Carregando consenso... $progress%"
                        progress < 75  -> "Carregando descritores... $progress%"
                        progress < 95  -> "Estabelecendo circuitos... $progress%"
                        else           -> "Conectado via Tor"
                    }

                    statusFlow.value = TorStatus.Connecting(message)
                    updateNotification(message)

                    if (progress >= 100) {
                        val onionAddress = torManager.getOnionAddress()
                        saveTempFile(onionAddress ?: "")

                        statusFlow.value = TorStatus.Connected
                        updateNotification("Conectado via Tor")
                        Log.i(TAG, "Tor reconectado: $onionAddress")
                        p2pEngine?.start()
                        return@collect
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao reconectar Tor: ${e.message}", e)
                statusFlow.value = TorStatus.StoppedTrying
                updateNotification("Falha na conexão")
            }
        }
    }

    suspend fun saveIdentity(name: String, pin: String): org.mksys.morse.core.service.SaveResult {
        return try {
            val identityKeys = identityManager.generateIdentityKeys()
            val tempData = loadTempFile()

            pendingIdentityName = name
            pendingIdentityPin = pin
            pendingIdentityOnion = tempData?.onionAddress ?: ""
            pendingIdentityKeys = identityKeys

            val writeOk = writeIdentity()
            if (!writeOk) throw Exception("Falha ao escrever identidade no banco")

            Log.i(TAG, "Identidade salva: name=$name, onion=$pendingIdentityOnion")
            org.mksys.morse.core.service.SaveResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar identidade: ${e.message}", e)
            org.mksys.morse.core.service.SaveResult.Error(e.message ?: "Erro desconhecido")
        }
    }

    private suspend fun writeIdentity(): Boolean {
        return try {
            val keys = pendingIdentityKeys ?: return false

            val opened = databaseManager.open(pendingIdentityPin)
            if (!opened) return false

            databaseManager.insertIdentity(
                displayName = pendingIdentityName,
                createdAt = System.currentTimeMillis(),
                ed25519PrivateKey = keys.ed25519PrivateKey,
                ed25519PublicKey = keys.ed25519PublicKey,
                x25519PrivateKey = keys.x25519PrivateKey,
                x25519PublicKey = keys.x25519PublicKey,
                onionAddress = pendingIdentityOnion
            )

            databaseManager.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao escrever identidade: ${e.message}", e)
            false
        }
    }

    suspend fun verifyIntegrity(): Boolean {
        val maxAttempts = 3

        for (attempt in 1..maxAttempts) {
            val status = databaseManager.hasRealIdentity()

            when (status) {
                is IdentityStatus.Confirmed -> {
                    identityStatusFlow.value = status
                    cleanupTempFile()
                    Log.i(TAG, "Integridade verificada, tentativa $attempt")
                    return true
                }
                is IdentityStatus.Absent -> {
                    Log.w(TAG, "Identidade ausente/incompleta, tentativa $attempt/$maxAttempts")
                    databaseManager.deleteDatabase()
                    if (attempt < maxAttempts) {
                        val writeOk = writeIdentity()
                        if (!writeOk) {
                            Log.e(TAG, "Regravação falhou na tentativa $attempt")
                        }
                    }
                }
                is IdentityStatus.Unknown -> {
                    Log.w(TAG, "KeyStore indisponível durante verificação, tentativa $attempt/$maxAttempts")
                    if (attempt < maxAttempts) {
                        delay(1000L)
                    }
                }
            }
        }

        Log.e(TAG, "Integridade falhou após $maxAttempts tentativas")
        return false
    }

    fun cleanupTempFile() {
        val tempFile = File(filesDir, TEMP_FILE_NAME)
        if (tempFile.exists()) tempFile.delete()
    }

    private fun saveTempFile(onionAddress: String) {
        val file = File(filesDir, TEMP_FILE_NAME)
        file.writeBytes(onionAddress.toByteArray())
    }

    private fun loadTempFile(): TempTorData? {
        val file = File(filesDir, TEMP_FILE_NAME)
        if (!file.exists()) return null
        val data = file.readBytes()
        return TempTorData(String(data))
    }

    data class TempTorData(val onionAddress: String)

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Morse Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Serviço em segundo plano do Morse"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Morse")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
