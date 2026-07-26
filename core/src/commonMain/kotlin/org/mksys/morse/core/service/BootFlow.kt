package org.mksys.morse.core.service

import org.mksys.morse.core.database.DatabaseManager
import org.mksys.morse.core.identity.IdentityManager
import org.mksys.morse.core.tor.TorManager

class BootFlow(
    private val identityManager: IdentityManager,
    private val databaseManager: DatabaseManager,
    private val torManager: TorManager
) {
    sealed class BootState {
        data object Initializing : BootState()
        data object WaitingConfig : BootState()
        data object Connected : BootState()
    }

    suspend fun execute(onStateChange: (BootState) -> Unit): BootState {
        onStateChange(BootState.Initializing)

        torManager.start()

        val onion = torManager.getOnionAddress() ?: ""

        onStateChange(BootState.WaitingConfig)
        return BootState.WaitingConfig
    }

    suspend fun login(pin: String): BootState {
        val opened = databaseManager.open(pin)
        if (!opened) return BootState.WaitingConfig

        val identity = databaseManager.getIdentity()
        databaseManager.close()

        return if (identity != null) BootState.Connected else BootState.WaitingConfig
    }
}
