package com.nurseflow.common.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

/** Random bearer secrets (refresh tokens, invitation links). Store only [sha256Hex], never the value. */
object OpaqueToken {
    private val random = SecureRandom()

    /** 256-bit value, base64url without padding (43 characters). */
    fun generate(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256Hex(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))

    const val PATTERN = "^[A-Za-z0-9_-]{43}$"
}
