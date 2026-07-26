package org.mksys.morse.database

import android.content.Context
import android.os.UserManager
import android.util.Log
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SQLiteDatabaseCorruptException
import net.sqlcipher.database.SQLiteException
import net.sqlcipher.database.SupportFactory
import org.mksys.morse.core.database.DatabaseManager
import org.mksys.morse.core.model.ConnectionRequestRecord
import org.mksys.morse.core.model.ContactRecord
import org.mksys.morse.core.model.IdentityStatus
import org.mksys.morse.core.model.IdentityRecord
import org.mksys.morse.core.model.MessageRecord
import org.mksys.morse.db.MorseDatabase
import java.security.SecureRandom
import java.io.File
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AndroidDatabaseManager(
    private val context: Context
) : DatabaseManager {

    companion object {
        private const val TAG = "AndroidDatabaseManager"
        private const val DB_NAME = "morse.db"
        private const val PREFS_NAME = "morse_db_prefs"
        private const val KEY_DB = "db_key"
        private const val KEY_DB_SALT = "db_salt"
        private const val PBKDF2_ITERATIONS = 600_000
        private const val KEY_LENGTH = 256
    }

    private var driver: AndroidSqliteDriver? = null
    private var database: MorseDatabase? = null

    override suspend fun open(pin: String): Boolean {
        return try {
            val encryptedPrefs = getEncryptedPrefs() ?: return false

            val storedKey = encryptedPrefs.getString(KEY_DB, null)

            val dbKey: ByteArray = if (storedKey != null) {
                android.util.Base64.decode(storedKey, android.util.Base64.NO_WRAP)
            } else {
                val storedSalt = encryptedPrefs.getString(KEY_DB_SALT, null)
                val salt = if (storedSalt != null) {
                    android.util.Base64.decode(storedSalt, android.util.Base64.NO_WRAP)
                } else {
                    generateDbSalt(encryptedPrefs)
                }
                val derived = deriveDbKey(pin, salt)
                encryptedPrefs.edit()
                    .putString(KEY_DB, android.util.Base64.encodeToString(derived, android.util.Base64.NO_WRAP))
                    .apply()
                derived
            }

            val factory = SupportFactory(dbKey)
            driver = AndroidSqliteDriver(
                schema = MorseDatabase.Schema,
                context = context,
                factory = factory,
                name = DB_NAME
            )
            database = MorseDatabase(driver!!)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir banco: ${e.message}", e)
            false
        }
    }

    override suspend fun close() {
        driver?.close()
        driver = null
        database = null
    }

    override fun isOpen(): Boolean = driver != null

    override suspend fun getIdentity(): IdentityRecord? {
        val db = database ?: return null
        return db.identityQueries.selectById().executeAsOneOrNull()?.let {
            IdentityRecord(
                displayName = it.display_name,
                createdAt = it.created_at,
                ed25519PrivateKey = it.ed25519_private_key,
                ed25519PublicKey = it.ed25519_public_key,
                x25519PrivateKey = it.x25519_private_key,
                x25519PublicKey = it.x25519_public_key,
                onionAddress = it.onion_address
            )
        }
    }

    override suspend fun insertIdentity(
        displayName: String,
        createdAt: Long,
        ed25519PrivateKey: ByteArray,
        ed25519PublicKey: ByteArray,
        x25519PrivateKey: ByteArray,
        x25519PublicKey: ByteArray,
        onionAddress: String
    ) {
        val db = database ?: return
        db.identityQueries.insertIdentity(
            display_name = displayName,
            created_at = createdAt,
            ed25519_private_key = ed25519PrivateKey,
            ed25519_public_key = ed25519PublicKey,
            x25519_private_key = x25519PrivateKey,
            x25519_public_key = x25519PublicKey,
            onion_address = onionAddress
        )
    }

    override suspend fun hasRealIdentity(): IdentityStatus {
        val encryptedPrefs = getEncryptedPrefs() ?: return IdentityStatus.Unknown
        val storedKey = encryptedPrefs.getString(KEY_DB, null) ?: return IdentityStatus.Absent

        return try {
            val dbKey = android.util.Base64.decode(storedKey, android.util.Base64.NO_WRAP)

            val tmpDriver = AndroidSqliteDriver(
                schema = MorseDatabase.Schema,
                context = context,
                factory = SupportFactory(dbKey),
                name = DB_NAME
            )
            val tmpDb = MorseDatabase(tmpDriver)
            val record = tmpDb.identityQueries.selectById().executeAsOneOrNull()
            tmpDriver.close()

            if (record == null) return IdentityStatus.Absent

            val complete = record.display_name.isNotBlank()
                && record.ed25519_private_key.isNotEmpty()
                && record.ed25519_public_key.isNotEmpty()
                && record.x25519_private_key.isNotEmpty()
                && record.x25519_public_key.isNotEmpty()
                && record.onion_address.isNotBlank()

            if (complete) IdentityStatus.Confirmed(record.display_name) else IdentityStatus.Absent
        } catch (e: SQLiteDatabaseCorruptException) {
            Log.e(TAG, "Banco corrompido ao verificar identidade", e)
            IdentityStatus.Absent
        } catch (e: SQLiteException) {
            if (e.message?.contains("file is not a database", ignoreCase = true) == true) {
                Log.e(TAG, "Arquivo não é banco válido — identidade ausente", e)
                IdentityStatus.Absent
            } else {
                Log.e(TAG, "SQLiteException ao verificar identidade: ${e.message}", e)
                IdentityStatus.Unknown
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar identidade: ${e.message}", e)
            IdentityStatus.Unknown
        }
    }

    override suspend fun deleteDatabase(): Boolean {
        return try {
            close()
            val dbFile = context.getDatabasePath(DB_NAME)
            if (dbFile.exists()) {
                dbFile.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao deletar banco: ${e.message}", e)
            false
        }
    }

    override suspend fun insertMessage(
        senderPublicKey: String,
        senderX25519PublicKey: String,
        senderOnionAddress: String,
        content: String,
        isOutgoing: Boolean,
        status: String,
        timestamp: Long,
        mimeType: String,
        audioBytes: ByteArray?,
        durationMs: Int?,
        senderMessageId: String?,
        deliveryTimestamp: Long?,
        createdAt: Long
    ) {
        val db = database ?: return
        db.messagesQueries.insertMessage(
            sender_public_key = senderPublicKey,
            sender_x25519_public_key = senderX25519PublicKey,
            sender_onion_address = senderOnionAddress,
            content = content,
            is_outgoing = if (isOutgoing) 1L else 0L,
            status = status,
            timestamp = timestamp,
            mime_type = mimeType,
            audio_bytes = audioBytes,
            duration_ms = durationMs?.toLong(),
            sender_message_id = senderMessageId,
            delivery_timestamp = deliveryTimestamp,
            created_at = createdAt
        )
    }

    override suspend fun getMessageBySenderMessageId(senderMessageId: String): Boolean {
        val db = database ?: return false
        return db.messagesQueries.selectBySenderMessageId(senderMessageId).executeAsOneOrNull() != null
    }

    override suspend fun updateMessageStatus(id: Long, status: String) {
        val db = database ?: return
        db.messagesQueries.updateStatus(status = status, id = id)
    }

    override suspend fun updateMessageStatusBySenderMessageId(senderMessageId: String, status: String, deliveryTimestamp: Long?) {
        val db = database ?: return
        db.messagesQueries.updateStatusBySenderMessageId(
            status = status,
            delivery_timestamp = deliveryTimestamp,
            sender_message_id = senderMessageId
        )
    }

    override suspend fun deleteMessagesBySender(senderPublicKey: String) {
        val db = database ?: return
        db.messagesQueries.deleteBySender(sender_public_key = senderPublicKey)
    }

    override suspend fun getContactByPublicKey(publicKeyB64: String): ContactRecord? {
        val db = database ?: return null
        return db.contactsQueries.getByPublicKey(publicKeyB64).executeAsOneOrNull()?.let {
            ContactRecord(
                publicKeyB64 = it.public_key,
                x25519PublicKeyB64 = it.x25519_public_key,
                onionAddress = it.onion_address,
                displayName = it.display_name,
                isBlocked = it.is_blocked == 1L,
                isDisconnected = it.is_disconnected == 1L,
                createdAt = it.created_at
            )
        }
    }

    override suspend fun insertContact(
        publicKey: String,
        x25519PublicKey: String,
        onionAddress: String,
        displayName: String,
        createdAt: Long
    ) {
        val db = database ?: return
        val existing = db.contactsQueries.getByPublicKey(publicKey).executeAsOneOrNull()
        if (existing == null) {
            db.contactsQueries.insertContact(
                public_key = publicKey,
                x25519_public_key = x25519PublicKey,
                onion_address = onionAddress,
                display_name = displayName,
                last_seen = 0L,
                avatar_color_index = -1L,
                is_blocked = 0L,
                is_disconnected = 0L,
                created_at = createdAt
            )
        } else {
            db.contactsQueries.insertContact(
                public_key = publicKey,
                x25519_public_key = x25519PublicKey,
                onion_address = onionAddress,
                display_name = if (displayName.isNotBlank()) displayName else existing.display_name,
                last_seen = existing.last_seen,
                avatar_color_index = existing.avatar_color_index,
                is_blocked = 0L,
                is_disconnected = 0L,
                created_at = existing.created_at
            )
        }
    }

    override suspend fun deleteContact(publicKey: String) {
        val db = database ?: return
        db.contactsQueries.deleteByPublicKey(public_key = publicKey)
    }

    override suspend fun updateContactLastSeen(publicKey: String, timestamp: Long) {
        val db = database ?: return
        db.contactsQueries.updateLastSeen(last_seen = timestamp, public_key = publicKey)
    }

    override suspend fun setContactDisconnected(publicKey: String) {
        val db = database ?: return
        db.contactsQueries.setDisconnected(public_key = publicKey)
    }

    override suspend fun setContactReactivated(publicKey: String) {
        val db = database ?: return
        db.contactsQueries.setReactivated(public_key = publicKey)
    }

    override suspend fun setContactBlocked(publicKey: String, blocked: Boolean) {
        val db = database ?: return
        db.contactsQueries.setBlocked(blocked = if (blocked) 1L else 0L, public_key = publicKey)
    }

    override suspend fun updateContactDisplayName(publicKey: String, displayName: String) {
        val db = database ?: return
        db.contactsQueries.updateDisplayName(display_name = displayName, public_key = publicKey)
    }

    override suspend fun insertConnectionRequest(
        publicKey: String,
        x25519PublicKey: String,
        onionAddress: String,
        displayName: String,
        timestamp: Long
    ) {
        val db = database ?: return
        db.connection_requestsQueries.insertRequest(
            public_key = publicKey,
            x25519_public_key = x25519PublicKey,
            onion_address = onionAddress,
            display_name = displayName,
            timestamp = timestamp
        )
    }

    override suspend fun getConnectionRequests(): List<ConnectionRequestRecord> {
        val db = database ?: return emptyList()
        return db.connection_requestsQueries.getAllRequests().executeAsList().map {
            ConnectionRequestRecord(
                publicKey = it.public_key,
                x25519PublicKey = it.x25519_public_key,
                onionAddress = it.onion_address,
                displayName = it.display_name,
                timestamp = it.timestamp
            )
        }
    }

    override suspend fun deleteConnectionRequest(publicKey: String) {
        val db = database ?: return
        db.connection_requestsQueries.deleteByPublicKey(public_key = publicKey)
    }

    override suspend fun insertAppEvent(
        type: String,
        senderPublicKey: String?,
        senderDisplayName: String?,
        message: String,
        timestamp: Long
    ) {
        val db = database ?: return
        db.app_eventsQueries.insertEvent(
            type = type,
            sender_public_key = senderPublicKey,
            sender_display_name = senderDisplayName,
            message = message,
            timestamp = timestamp,
            is_read = 0L
        )
    }

    override suspend fun getUnreadEventCount(): Long {
        val db = database ?: return 0L
        return db.app_eventsQueries.getUnreadCount().executeAsOne()
    }

    override suspend fun markAppEventAsRead(id: Long) {
        val db = database ?: return
        db.app_eventsQueries.markAsRead(id = id)
    }

    override suspend fun markAllAppEventsAsRead() {
        val db = database ?: return
        db.app_eventsQueries.markAllAsRead()
    }

    override suspend fun getAllContacts(): List<ContactRecord> {
        val db = database ?: return emptyList()
        return db.contactsQueries.getAllContacts().executeAsList().map {
            ContactRecord(
                publicKeyB64 = it.public_key,
                x25519PublicKeyB64 = it.x25519_public_key,
                onionAddress = it.onion_address,
                displayName = it.display_name,
                isBlocked = it.is_blocked == 1L,
                isDisconnected = it.is_disconnected == 1L,
                createdAt = it.created_at
            )
        }
    }

    override suspend fun getMessagesBySender(senderPublicKey: String): List<MessageRecord> {
        val db = database ?: return emptyList()
        return db.messagesQueries.selectBySender(senderPublicKey).executeAsList().map {
            MessageRecord(
                id = it.id,
                senderPublicKey = it.sender_public_key,
                senderX25519PublicKey = it.sender_x25519_public_key,
                senderOnionAddress = it.sender_onion_address,
                content = it.content,
                isOutgoing = it.is_outgoing == 1L,
                status = it.status,
                timestamp = it.timestamp,
                mimeType = it.mime_type,
                audioBytes = it.audio_bytes,
                durationMs = it.duration_ms?.toInt(),
                senderMessageId = it.sender_message_id,
                deliveryTimestamp = it.delivery_timestamp,
                createdAt = it.created_at
            )
        }
    }

    override suspend fun getLastMessagePerContact(): List<MessageRecord> {
        val db = database ?: return emptyList()
        return db.messagesQueries.getLastMessagePerContact().executeAsList().map {
            MessageRecord(
                id = it.id,
                senderPublicKey = it.sender_public_key,
                senderX25519PublicKey = it.sender_x25519_public_key,
                senderOnionAddress = it.sender_onion_address,
                content = it.content,
                isOutgoing = it.is_outgoing == 1L,
                status = it.status,
                timestamp = it.timestamp,
                mimeType = it.mime_type,
                audioBytes = it.audio_bytes,
                durationMs = it.duration_ms?.toInt(),
                senderMessageId = it.sender_message_id,
                deliveryTimestamp = it.delivery_timestamp,
                createdAt = it.created_at
            )
        }
    }

    override suspend fun getUnreadConnectionRequestCount(): Long {
        val db = database ?: return 0L
        return db.connection_requestsQueries.getAllRequests().executeAsList().size.toLong()
    }

    private fun getEncryptedPrefs(): android.content.SharedPreferences? {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        if (!userManager.isUserUnlocked) {
            Log.d(TAG, "Dispositivo ainda bloqueado, KeyStore indisponível")
            return null
        }

        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar EncryptedSharedPreferences: ${e.message}", e)
            null
        }
    }

    private fun generateDbSalt(prefs: android.content.SharedPreferences): ByteArray {
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_DB_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .apply()
        return salt
    }

    private fun deriveDbKey(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return keyBytes
    }
}
