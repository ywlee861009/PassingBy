package com.kero.passingby.journey

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class PreviewCodeTest {
    private fun hash(code: String) = MessageDigest.getInstance("SHA-256").digest(code.toByteArray())
        .joinToString("") { "%02x".format(it) }

    @Test fun emptyAndMalformedConfigurationCannotUnlock() {
        for (hash in listOf("", "anything", "f".repeat(63), "z".repeat(64))) {
            val gate = PreviewCode(hash)
            assertFalse(gate.enabled)
            assertFalse(gate.matches("anything"))
        }
    }
    @Test fun onlyConfiguredCodeUnlocksAndWhitespaceFromPastingIsIgnored() {
        val gate = PreviewCode(hash("local-test-pass"))
        assertTrue(gate.enabled)
        assertTrue(gate.matches("local-test-pass"))
        assertTrue(gate.matches(" local-test-pass\n"))
        assertFalse(gate.matches("LOCAL-TEST-PASS"))
        assertFalse(gate.matches("wrong"))
        assertFalse(gate.matches(""))
        assertFalse(gate.matches("x".repeat(129)))
    }
}
