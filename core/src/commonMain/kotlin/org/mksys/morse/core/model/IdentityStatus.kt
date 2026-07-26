package org.mksys.morse.core.model

sealed class IdentityStatus {
    data class Confirmed(val displayName: String) : IdentityStatus()
    data object Absent : IdentityStatus()
    data object Unknown : IdentityStatus()
}
