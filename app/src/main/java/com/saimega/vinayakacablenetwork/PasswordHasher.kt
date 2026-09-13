package com.saimega.vinayakacablenetwork

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Salted SHA-256 password hashing. Not a substitute for Firebase Auth or
 * a proper KDF like bcrypt/Argon2 — a deliberate, documented tradeoff
 * (see the real-user-accounts spec) accepted in place of adopting
 * Firebase Authentication for this app.
 *
 * Uses only java.security (no android.util.Base64): that class requires
 * API 26 to decode standalone and can't be exercised under plain JUnit
 * (testDebugUnitTest has no Android framework), so salts/hashes are
 * hex-encoded manually instead — correct on this app's minSdk 24 and
 * trivially unit-testable.
 */
object PasswordHasher {

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(Charsets.UTF_8))
        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hashBytes.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
