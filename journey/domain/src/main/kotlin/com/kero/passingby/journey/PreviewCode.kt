package com.kero.passingby.journey

import java.security.MessageDigest

/** No plaintext code is retained, persisted or sent to telemetry. Empty configuration fails closed. */
class PreviewCode(private val expected: String) {
    val enabled = expected.matches(Regex("[0-9a-f]{64}"))
    fun matches(input: String): Boolean {
        if (!enabled || input.isBlank() || input.length > 128) return false
        val actual = MessageDigest.getInstance("SHA-256").digest(input.trim().toByteArray(Charsets.UTF_8))
        val target = expected.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return MessageDigest.isEqual(actual, target)
    }
}
