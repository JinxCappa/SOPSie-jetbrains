package com.sopsie.util

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal

/**
 * Creates temp files that hold decrypted secrets.
 *
 * On POSIX filesystems the file is created with 0600 permissions atomically,
 * so no other local user can read the plaintext and there is no window
 * between creation and a chmod call where the file is world-readable.
 *
 * On Windows, the file is created with default permissions and then the
 * ACL is narrowed to the current user immediately afterwards. If writing
 * or ACL restriction fails the file is deleted to avoid leaving plaintext
 * under inherited permissions.
 *
 * `Files.createTempFile` also randomises the filename, eliminating the
 * predictable-path pre-create/symlink race the previous deterministic
 * naming scheme allowed.
 */
object SecureTempFiles {

    private val SUPPORTED_VIEWS: Set<String> by lazy {
        try {
            FileSystems.getDefault().supportedFileAttributeViews()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private val IS_POSIX: Boolean by lazy { SUPPORTED_VIEWS.contains("posix") }
    private val HAS_ACL: Boolean by lazy { SUPPORTED_VIEWS.contains("acl") }

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
        // Register for deletion immediately so an exception below still
        // schedules cleanup for the newly created file.
        path.toFile().deleteOnExit()

        try {
            if (!IS_POSIX && HAS_ACL) {
                restrictAclToOwner(path)
            }
            Files.writeString(path, content)
        } catch (ex: Throwable) {
            try {
                Files.deleteIfExists(path)
            } catch (_: Exception) {
                // best-effort
            }
            throw ex
        }
        return path
    }

    /**
     * Replace the file's ACL with a single allow entry for the current
     * owner granting full access and no entries for any other principal.
     */
    private fun restrictAclToOwner(path: Path) {
        val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
            ?: return
        val owner: UserPrincipal = view.owner
        val entry = AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(owner)
            .setPermissions(
                AclEntryPermission.READ_DATA,
                AclEntryPermission.WRITE_DATA,
                AclEntryPermission.APPEND_DATA,
                AclEntryPermission.READ_ATTRIBUTES,
                AclEntryPermission.WRITE_ATTRIBUTES,
                AclEntryPermission.READ_NAMED_ATTRS,
                AclEntryPermission.WRITE_NAMED_ATTRS,
                AclEntryPermission.READ_ACL,
                AclEntryPermission.WRITE_ACL,
                AclEntryPermission.DELETE,
                AclEntryPermission.SYNCHRONIZE
            )
            .build()
        view.acl = listOf(entry)
    }
}
