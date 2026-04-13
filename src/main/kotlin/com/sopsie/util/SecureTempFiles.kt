package com.sopsie.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions

/**
 * Creates temp files that hold decrypted secrets.
 *
 * On POSIX filesystems the file is created with 0600 permissions atomically,
 * so no other local user can read the plaintext and there is no window
 * between creation and a chmod call where the file is world-readable.
 *
 * `Files.createTempFile` also randomises the filename, eliminating the
 * predictable-path pre-create/symlink race the previous deterministic
 * naming scheme allowed.
 */
object SecureTempFiles {

    private val IS_POSIX: Boolean by lazy {
        try {
            java.nio.file.FileSystems.getDefault()
                .supportedFileAttributeViews()
                .contains("posix")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Create a temp file with restrictive permissions and write [content] to it.
     * Registers the file for best-effort deletion on JVM exit.
     */
    fun create(prefix: String, suffix: String, content: String): Path {
        // Files.createTempFile requires a prefix of at least 3 characters
        val safePrefix = prefix.padEnd(3, '_')
        val attrs: Array<FileAttribute<*>> = if (IS_POSIX) {
            arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } else {
            emptyArray()
        }
        val path = Files.createTempFile(safePrefix, suffix, *attrs)
        Files.writeString(path, content)
        path.toFile().deleteOnExit()
        return path
    }
}
