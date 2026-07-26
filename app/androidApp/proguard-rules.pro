# =============================================================================
# Morse - Regras ProGuard/R8
# Projeto: Morse (KMP + Compose Multiplatform)
# Data: 2026-07-25
# =============================================================================

# ---------------------------------------------------------------------------
# 1. NOSSE APP - classes do manifesto, data classes, sealed classes
# ---------------------------------------------------------------------------

# Todas as classes do nosso app
-keep class org.mksys.morse.** { *; }

# Services declarados no manifest (bindService com LocalBinder cast)
-keep class org.mksys.morse.MorseService { *; }
-keep class org.mksys.morse.MorseService$* { *; }
-keep class org.mksys.morse.MainActivity { *; }
-keep class org.mksys.morse.BootReceiver { *; }

# Data classes com ByteArray (equals/hashCode customizados)
-keep class org.mksys.morse.core.model.MessageRecord { *; }
-keep class org.mksys.morse.core.model.IdentityRecord { *; }
-keep class org.mksys.morse.core.model.IdentityKeys { *; }
-keep class org.mksys.morse.core.model.ContactRecord { *; }
-keep class org.mksys.morse.core.model.ConnectionRequestRecord { *; }

# Sealed classes - subclasses precisam existir para pattern matching
-keep class org.mksys.morse.core.p2p.P2PEvent** { *; }
-keep class org.mksys.morse.core.service.TorStatus** { *; }
-keep class org.mksys.morse.core.service.SaveResult** { *; }
-keep class org.mksys.morse.core.model.IdentityStatus** { *; }
-keep class org.mksys.morse.core.service.BootState** { *; }

# P2P engine
-keep class org.mksys.morse.p2p.** { *; }

# WireMessage (data class com ByteArray)
-keep class org.mksys.morse.p2p.MessageProtocol$WireMessage { *; }

# ---------------------------------------------------------------------------
# 2. BOUNCYCASTLE - crypto provider + algoritmos
# ---------------------------------------------------------------------------

-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ---------------------------------------------------------------------------
# 3. TOR - service binding + control connection
# ---------------------------------------------------------------------------

-keep class org.torproject.jni.TorService { *; }
-keep class org.torproject.jni.TorService$* { *; }
-keep class net.freehaven.tor.control.** { *; }
-dontwarn org.torproject.**
-dontwarn net.freehaven.**

# ---------------------------------------------------------------------------
# 4. SQLCIPHER - native library + driver
# ---------------------------------------------------------------------------

-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.SupportFactory { *; }
-dontwarn net.sqlcipher.**

# ---------------------------------------------------------------------------
# 5. SQLDELIGHT - generated code com KClass reflection
# ---------------------------------------------------------------------------

# Database interface + impl (MorseDatabase::class.newInstance(driver))
-keep class org.mksys.morse.db.MorseDatabase { *; }
-keep class org.mksys.morse.db.MorseDatabase$* { *; }
-keep class org.mksys.morse.db.core.MorseDatabaseImpl { *; }

# Todas as queries e data classes geradas
-keep class org.mksys.morse.db.**Queries { *; }
-keep class org.mksys.morse.db.**Queries$* { *; }
-keep class org.mksys.morse.db.Messages { *; }
-keep class org.mksys.morse.db.Identity { *; }
-keep class org.mksys.morse.db.Contacts { *; }
-keep class org.mksys.morse.db.Connection_requests { *; }
-keep class org.mksys.morse.db.App_events { *; }

# ---------------------------------------------------------------------------
# 6. ANDROIDX SECURITY - EncryptedSharedPreferences + KeyStore
# ---------------------------------------------------------------------------

-keep class androidx.security.** { *; }
-dontwarn androidx.security.**

# ---------------------------------------------------------------------------
# 7. JAVAX.CRYPTO - algoritmos não podem ser renomeados
# ---------------------------------------------------------------------------

-keepclassmembers class javax.crypto.** {
    *;
}

-keepclassmembers class javax.security.auth.** {
    *;
}

# ---------------------------------------------------------------------------
# 8. COMPOSE - preservar funções Composable
# ---------------------------------------------------------------------------

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---------------------------------------------------------------------------
# 9. WORK MANAGER
# ---------------------------------------------------------------------------

-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# ---------------------------------------------------------------------------
# 10. KOTLINX COROUTINES
# ---------------------------------------------------------------------------

-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# 11. GOOGLE TINK (via SQLCipher/EncryptedSharedPreferences)
# ---------------------------------------------------------------------------

-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
-dontwarn com.google.crypto.tink.**

# ---------------------------------------------------------------------------
# 12. ATRIBUTOS - preservar para debug
# ---------------------------------------------------------------------------

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
