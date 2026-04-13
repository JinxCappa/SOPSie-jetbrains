package com.sopsie.services

import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Platform test for the rename/move rekeying logic in [FileStateTracker].
 *
 * Pure-JVM tests can verify the simple Set add/remove paths, but the
 * rekey-on-rename behavior is wired through the project messageBus and
 * [com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent]
 * (rename) / [com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent]
 * (move). Those events only fire from a real VFS, so they need a platform
 * test to exercise the full subscription chain.
 *
 * Without the rekey logic, saving a decrypted file after renaming it
 * would silently skip re-encryption and flush plaintext to disk under the
 * new name — a real bug the listener exists to prevent.
 */
class FileStateTrackerPlatformTest : BasePlatformTestCase() {

    private lateinit var tracker: FileStateTracker

    override fun setUp() {
        super.setUp()
        tracker = FileStateTracker.getInstance(project)
    }

    override fun tearDown() {
        try {
            tracker.clear()
        } finally {
            super.tearDown()
        }
    }

    fun `test isMarkedDecrypted is false for unknown files`() {
        val file = myFixture.configureByText("unknown.yaml", "x: 1").virtualFile
        assertFalse(tracker.isMarkedDecrypted(file))
    }

    fun `test markDecrypted then isMarkedDecrypted returns true`() {
        val file = myFixture.configureByText("a.yaml", "x: 1").virtualFile
        tracker.markDecrypted(file)
        assertTrue(tracker.isMarkedDecrypted(file))
    }

    fun `test markEncrypted clears the marker`() {
        val file = myFixture.configureByText("b.yaml", "x: 1").virtualFile
        tracker.markDecrypted(file)
        tracker.markEncrypted(file)
        assertFalse(tracker.isMarkedDecrypted(file))
    }

    fun `test rename rekeys the entry to the new path`() {
        val file = myFixture.configureByText("before.yaml", "x: 1").virtualFile
        val oldPath = file.path

        tracker.markDecrypted(file)
        assertTrue("seeded under old path", tracker.isMarkedDecrypted(oldPath))

        WriteAction.runAndWait<Throwable> {
            file.rename(this, "after.yaml")
        }

        assertFalse("old path no longer tracked", tracker.isMarkedDecrypted(oldPath))
        assertTrue("new path is now tracked", tracker.isMarkedDecrypted(file))
        assertTrue(file.name == "after.yaml")
    }

    fun `test rename does NOT spontaneously add an entry when there was none`() {
        val file = myFixture.configureByText("untracked.yaml", "x: 1").virtualFile
        // Do NOT markDecrypted

        WriteAction.runAndWait<Throwable> {
            file.rename(this, "untracked-renamed.yaml")
        }

        assertFalse(tracker.isMarkedDecrypted(file))
    }
}
