package org.mksys.morse

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform