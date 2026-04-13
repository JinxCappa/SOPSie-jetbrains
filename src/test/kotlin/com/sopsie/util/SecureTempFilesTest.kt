package com.sopsie.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class SecureTempFilesTest {

    private val isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
    private val created = mutableListOf<Path>()

    @After fun cleanup() {
        created.forEach { runCatching { Files.deleteIfExists(it) } }
    }

    private fun create(prefix: String, suffix: String, content: String): Path {
        val p = SecureTempFiles.create(prefix, suffix, content)
        created.add(p)
        return p
    }

    @Test fun `creates file with the requested content`() {
        val p = create("test-", ".sops-edit.yaml", "plaintext-secret")
        assertEquals("plaintext-secret", Files.readString(p))
    }

    @Test fun `pads short prefixes to at least three characters`() {
        // Files.createTempFile rejects prefixes shorter than 3 characters.
        // SecureTempFiles pads with underscores so callers do not have to.
        val p = create("a", ".tmp", "data")
        assertTrue(Files.exists(p))
        assertTrue(p.fileName.toString().startsWith("a__"))
    }

    @Test fun `applies POSIX 0600 permissions atomically`() {
        assumeTrue("POSIX-only test", isPosix)
        val p = create("perms-", ".tmp", "x")
        val perms = Files.getPosixFilePermissions(p)
        assertEquals(PosixFilePermissions.fromString("rw-------"), perms)
    }

    @Test fun `produces unique paths across calls with the same prefix`() {
        val a = create("dup-", ".tmp", "A")
        val b = create("dup-", ".tmp", "B")
        assertNotEquals(a, b)
        assertEquals("A", Files.readString(a))
        assertEquals("B", Files.readString(b))
    }

    @Test fun `preserves the suffix`() {
        val p = create("sfx-", ".sops-edit.json", "{}")
        assertTrue(p.fileName.toString().endsWith(".sops-edit.json"))
    }

    @Test fun `empty content writes an empty file`() {
        val p = create("empty-", ".tmp", "")
        assertEquals("", Files.readString(p))
        assertEquals(0L, Files.size(p))
    }
}
