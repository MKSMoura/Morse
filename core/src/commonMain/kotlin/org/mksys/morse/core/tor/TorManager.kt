package org.mksys.morse.core.tor

import kotlinx.coroutines.flow.StateFlow

interface TorManager {
    val bootstrapProgress: StateFlow<Int>
    suspend fun start()
    suspend fun stop()
    suspend fun isReady(): Boolean
    suspend fun getOnionAddress(): String?
    fun getPort(): Int
}
