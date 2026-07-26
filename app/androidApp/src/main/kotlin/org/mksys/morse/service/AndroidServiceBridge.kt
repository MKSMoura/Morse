package org.mksys.morse.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.mksys.morse.MorseService
import org.mksys.morse.core.model.ConnectionRequestRecord
import org.mksys.morse.core.model.ContactRecord
import org.mksys.morse.core.model.IdentityStatus
import org.mksys.morse.core.model.MessageRecord
import org.mksys.morse.core.p2p.P2PEvent
import org.mksys.morse.core.service.SaveResult
import org.mksys.morse.core.service.ServiceBridge
import org.mksys.morse.core.service.TorStatus

class AndroidServiceBridge(
    private val context: Context
) : ServiceBridge {

    private var service: MorseService? = null
    private var bound = false
    private val internalStatusFlow = MutableStateFlow<TorStatus>(TorStatus.Starting)
    private val internalIdentityFlow = MutableStateFlow<IdentityStatus>(IdentityStatus.Unknown)
    private var collectJob: Job? = null
    private var identityCollectJob: Job? = null
    private var p2pCollectJob: Job? = null
    private val internalP2PFlow = MutableStateFlow<P2PEvent?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as MorseService.LocalBinder
            service = localBinder.getService()
            bound = true

            collectJob?.cancel()
            collectJob = CoroutineScope(Dispatchers.Main).launch {
                service?.observeStatus()?.collect { status ->
                    internalStatusFlow.value = status
                }
            }

            identityCollectJob?.cancel()
            identityCollectJob = CoroutineScope(Dispatchers.Main).launch {
                service?.observeIdentity()?.collect { status ->
                    internalIdentityFlow.value = status
                }
            }

            p2pCollectJob?.cancel()
            p2pCollectJob = CoroutineScope(Dispatchers.Main).launch {
                service?.observeP2PEvents()?.collect { event ->
                    internalP2PFlow.value = event
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            collectJob?.cancel()
            identityCollectJob?.cancel()
            p2pCollectJob?.cancel()
            service = null
            bound = false
            internalStatusFlow.value = TorStatus.Starting
            internalIdentityFlow.value = IdentityStatus.Unknown
            internalP2PFlow.value = null
        }
    }

    fun bind() {
        val intent = Intent(context, MorseService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (bound) {
            context.unbindService(connection)
            bound = false
        }
    }

    override fun observeStatus(): Flow<TorStatus> {
        return internalStatusFlow.asStateFlow()
    }

    override fun observeIdentity(): Flow<IdentityStatus> {
        return internalIdentityFlow.asStateFlow()
    }

    override fun observeP2PEvents(): Flow<P2PEvent>? {
        return internalP2PFlow.asStateFlow().filterNotNull()
    }

    override suspend fun isServiceRunning(): Boolean = bound

    override suspend fun startService() {
        val intent = Intent(context, MorseService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    override suspend fun retryConnection() {
        service?.retryConnection()
    }

    override suspend fun saveIdentity(name: String, pin: String): SaveResult {
        return service?.saveIdentity(name, pin) ?: SaveResult.Error("Serviço não conectado")
    }

    override suspend fun verifyIntegrity(): Boolean {
        return service?.verifyIntegrity() ?: false
    }

    override suspend fun confirmRetry() {
        service?.confirmRetry()
    }

    override suspend fun leaveForLater() {
        service?.leaveForLater()
    }

    override suspend fun sendMessage(
        recipientPublicKeyB64: String,
        recipientX25519PublicKeyB64: String,
        recipientOnionAddress: String,
        content: String,
        mimeType: String,
        messageId: String
    ): Boolean {
        return service?.sendMessage(
            recipientPublicKeyB64, recipientX25519PublicKeyB64, recipientOnionAddress, content, mimeType, messageId
        ) ?: false
    }

    override suspend fun sendConnectionRequest(targetOnion: String, targetX25519PublicKeyB64: String): Boolean {
        return service?.sendConnectionRequest(targetOnion, targetX25519PublicKeyB64) ?: false
    }

    override suspend fun acceptConnectionRequest(
        theirPublicKeyB64: String,
        theirOnionAddress: String,
        theirX25519PubB64: String,
        displayName: String
    ): Boolean {
        return service?.acceptConnectionRequest(theirPublicKeyB64, theirOnionAddress, theirX25519PubB64, displayName) ?: false
    }

    override suspend fun sendDisconnect(targetOnion: String, targetX25519PublicKeyB64: String): Boolean {
        return service?.sendDisconnect(targetOnion, targetX25519PublicKeyB64) ?: false
    }

    override fun setConversationActive(contactPublicKeyB64: String, active: Boolean) {
        service?.setConversationActive(contactPublicKeyB64, active)
    }

    override suspend fun getAllContacts(): List<ContactRecord> {
        return service?.getAllContacts() ?: emptyList()
    }

    override suspend fun getMessagesBySender(senderPublicKey: String): List<MessageRecord> {
        return service?.getMessagesBySender(senderPublicKey) ?: emptyList()
    }

    override suspend fun getLastMessagePerContact(): List<MessageRecord> {
        return service?.getLastMessagePerContact() ?: emptyList()
    }

    override suspend fun getConnectionRequests(): List<ConnectionRequestRecord> {
        return service?.getConnectionRequests() ?: emptyList()
    }

    override suspend fun getUnreadEventCount(): Long {
        return service?.getUnreadEventCount() ?: 0L
    }

    override suspend fun markAllAppEventsAsRead() {
        service?.markAllAppEventsAsRead()
    }

    override suspend fun deleteContact(publicKey: String) {
        service?.deleteContact(publicKey)
    }
}
