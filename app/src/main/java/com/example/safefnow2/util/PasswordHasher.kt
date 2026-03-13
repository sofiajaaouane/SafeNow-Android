package com.example.safefnow2.util

import java.security.MessageDigest
import java.util.Locale

// Hash password with SHA-256 and app salt for storage.
object PasswordHasher {
    private const val SALT = "SafeNow2026"
    private const val ALGORITHM = "SHA-256"

    fun hash(password: String): String {
        val input = SALT + password
        val digest = MessageDigest.getInstance(ALGORITHM)
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(Locale.US, it) }
    }

    fun verify(password: String, storedHash: String): Boolean {
        return hash(password) == storedHash
    }
}
