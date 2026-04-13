package com.sopsie.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SopsErrorTest {

    @Test fun `cliNotFound carries the cli path and is not recoverable`() {
        val e = SopsError.cliNotFound("/nonexistent/sops")
        assertEquals(SopsErrorType.CLI_NOT_FOUND, e.type)
        assertTrue(e.message.contains("/nonexistent/sops"))
        assertFalse(e.recoverable)
        assertNotNull(e.suggestedAction)
    }

    @Test fun `non-CLI errors are recoverable by default`() {
        assertTrue(SopsError.timeout(5000).recoverable)
        assertTrue(SopsError.keyAccessDenied("d").recoverable)
        assertTrue(SopsError.configNotFound("d").recoverable)
        assertTrue(SopsError.invalidFile("d").recoverable)
        assertTrue(SopsError.encryptionFailed("d").recoverable)
        assertTrue(SopsError.decryptionFailed("d").recoverable)
        assertTrue(SopsError.unknown("d", 1).recoverable)
    }

    @Test fun `timeout includes timeout value in message`() {
        val e = SopsError.timeout(7500)
        assertEquals(SopsErrorType.TIMEOUT, e.type)
        assertTrue(e.message.contains("7500"))
    }

    @Test fun `keyAccessDenied carries details and a key-related suggested action`() {
        val e = SopsError.keyAccessDenied("gpg failure detail")
        assertEquals(SopsErrorType.KEY_ACCESS_DENIED, e.type)
        assertEquals("gpg failure detail", e.details)
        assertNotNull(e.suggestedAction)
        assertTrue(
            e.suggestedAction!!.contains("age", ignoreCase = true) ||
                e.suggestedAction!!.contains("key", ignoreCase = true)
        )
    }

    @Test fun `configNotFound suggests creating a sops_yaml file`() {
        val e = SopsError.configNotFound("nothing in parent dirs")
        assertEquals(SopsErrorType.CONFIG_NOT_FOUND, e.type)
        assertTrue(e.suggestedAction!!.contains(".sops.yaml"))
    }

    @Test fun `invalidFile carries details and a yaml-or-json suggestion`() {
        val e = SopsError.invalidFile("bad json at line 5")
        assertEquals(SopsErrorType.INVALID_FILE, e.type)
        assertNotNull(e.suggestedAction)
    }

    @Test fun `encryptionFailed and decryptionFailed have no suggested action`() {
        assertNull(SopsError.encryptionFailed("d").suggestedAction)
        assertNull(SopsError.decryptionFailed("d").suggestedAction)
    }

    @Test fun `unknown formats code in the message`() {
        val e = SopsError.unknown("boom", 42)
        assertEquals(SopsErrorType.UNKNOWN, e.type)
        assertTrue(e.message.contains("42"))
    }

    @Test fun `SopsException wraps the error and exposes its message`() {
        val err = SopsError.timeout(1000)
        val ex = SopsException(err)
        assertEquals(err, ex.error)
        assertEquals(err.message, ex.message)
    }
}
