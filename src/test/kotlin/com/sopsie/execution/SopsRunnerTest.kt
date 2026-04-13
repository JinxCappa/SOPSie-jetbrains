package com.sopsie.execution

import com.sopsie.model.SopsErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SopsRunnerTest {

    private val runner = SopsRunner()

    @Test fun `classifies could not decrypt as KEY_ACCESS_DENIED`() {
        val e = runner.parseError("sops: could not decrypt data key with PGP key", 1)
        assertEquals(SopsErrorType.KEY_ACCESS_DENIED, e.type)
        assertNotNull(e.suggestedAction)
    }

    @Test fun `classifies failed to get the data key as KEY_ACCESS_DENIED`() {
        val e = runner.parseError("Failed to get the data key required to decrypt", 1)
        assertEquals(SopsErrorType.KEY_ACCESS_DENIED, e.type)
    }

    @Test fun `classifies cannot find key as KEY_ACCESS_DENIED`() {
        val e = runner.parseError("cannot find key for given fingerprint", 1)
        assertEquals(SopsErrorType.KEY_ACCESS_DENIED, e.type)
    }

    @Test fun `classifies config file not found as CONFIG_NOT_FOUND`() {
        val e = runner.parseError("config file not found in any parent directory", 1)
        assertEquals(SopsErrorType.CONFIG_NOT_FOUND, e.type)
    }

    @Test fun `classifies any sops_yaml mention as CONFIG_NOT_FOUND`() {
        // Locks in current behavior: the matcher treats any .sops.yaml mention
        // as a missing-config signal.
        val e = runner.parseError("error reading .sops.yaml: permission denied", 1)
        assertEquals(SopsErrorType.CONFIG_NOT_FOUND, e.type)
    }

    @Test fun `classifies yaml parse error as INVALID_FILE`() {
        val e = runner.parseError("yaml: line 5: did not find expected key", 1)
        assertEquals(SopsErrorType.INVALID_FILE, e.type)
    }

    @Test fun `classifies error parsing as INVALID_FILE`() {
        val e = runner.parseError("Error parsing JSON: unexpected token", 1)
        assertEquals(SopsErrorType.INVALID_FILE, e.type)
    }

    @Test fun `classifies generic encrypt failure as ENCRYPTION_FAILED`() {
        val e = runner.parseError("failed to encrypt: something went wrong", 1)
        assertEquals(SopsErrorType.ENCRYPTION_FAILED, e.type)
    }

    @Test fun `classifies generic decrypt failure as DECRYPTION_FAILED`() {
        val e = runner.parseError("failed to decrypt some value", 1)
        assertEquals(SopsErrorType.DECRYPTION_FAILED, e.type)
    }

    @Test fun `falls back to UNKNOWN when no marker matches`() {
        val e = runner.parseError("some unrelated failure", 42)
        assertEquals(SopsErrorType.UNKNOWN, e.type)
        assertTrue(e.message.contains("42"))
    }

    @Test fun `KEY_ACCESS_DENIED takes precedence over generic decrypt match`() {
        val e = runner.parseError("could not decrypt: failed to decrypt data", 1)
        assertEquals(SopsErrorType.KEY_ACCESS_DENIED, e.type)
    }
}
