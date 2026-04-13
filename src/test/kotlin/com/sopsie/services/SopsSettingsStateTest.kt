package com.sopsie.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SopsSettingsService extends SimplePersistentStateComponent and depends on
 * IntelliJ's persistence layer, so the service itself is best covered by
 * platform tests. The state class itself is plain data with default values
 * — covered here without IDE setup.
 */
class SopsSettingsStateTest {

    @Test fun `default sopsPath is "sops"`() {
        assertEquals("sops", SopsSettingsState().sopsPath)
    }

    @Test fun `default decryption timeout is 30 seconds`() {
        assertEquals(30000, SopsSettingsState().decryptionTimeout)
    }

    @Test fun `confirmation flags default on`() {
        val s = SopsSettingsState()
        assertTrue(s.confirmRotate)
        assertTrue(s.confirmUpdateKeys)
    }

    @Test fun `default behaviors are conservative`() {
        val s = SopsSettingsState()
        assertEquals(OpenBehavior.SHOW_ENCRYPTED, s.openBehavior)
        assertEquals(SaveBehavior.MANUAL_ENCRYPT, s.saveBehavior)
        assertEquals(DecryptedViewMode.PREVIEW, s.decryptedViewMode)
    }

    @Test fun `editor flags default on`() {
        val s = SopsSettingsState()
        assertTrue(s.showStatusBar)
        assertTrue(s.autoCloseTab)
        assertTrue(s.openDecryptedBeside)
        assertTrue(s.autoClosePairedTab)
    }

    @Test fun `debug logging defaults off`() {
        assertEquals(false, SopsSettingsState().enableDebugLogging)
    }

    @Test fun `state mutations round-trip`() {
        val s = SopsSettingsState()
        s.sopsPath = "/usr/local/bin/sops"
        s.decryptionTimeout = 45_000
        s.openBehavior = OpenBehavior.AUTO_DECRYPT
        s.saveBehavior = SaveBehavior.AUTO_ENCRYPT

        assertEquals("/usr/local/bin/sops", s.sopsPath)
        assertEquals(45_000, s.decryptionTimeout)
        assertEquals(OpenBehavior.AUTO_DECRYPT, s.openBehavior)
        assertEquals(SaveBehavior.AUTO_ENCRYPT, s.saveBehavior)
    }
}
