package org.mksys.morse.core.identity

import org.mksys.morse.core.model.IdentityKeys

interface IdentityManager {
    suspend fun generateIdentityKeys(): IdentityKeys
}
