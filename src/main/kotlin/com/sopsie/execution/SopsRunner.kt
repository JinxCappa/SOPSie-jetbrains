package com.sopsie.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.sopsie.model.SopsError
import com.sopsie.model.SopsErrorType
import com.sopsie.model.SopsException
import com.sopsie.services.SopsSettingsService
import com.sopsie.util.SecureTempFiles
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

private val LOG = logger<SopsRunner>()

/**
 * Wrapper for SOPS CLI operations
 */
@Service(Service.Level.APP)
class SopsRunner {

    private val settings: SopsSettingsService
        get() = SopsSettingsService.getInstance()

    /**
     * Get the working directory for SOPS commands.
     * SOPS looks for .sops.yaml from CWD and walks up the directory tree.
     */
    private fun getWorkingDirectory(filePath: String): File {
        return File(filePath).parentFile
    }

    /**
     * Determine the input type based on file extension
     */
    private fun getInputType(extension: String): String {
        return when (extension.lowercase()) {
            "json" -> "json"
            "env" -> "dotenv"
            "ini" -> "ini"
            "yaml", "yml" -> "yaml"
            else -> "binary"
        }
    }

    /**
     * Decrypt a file and return the decrypted content.
     * Automatically detects file type from extension.
     */
    fun decrypt(filePath: String): String {
        val ext = File(filePath).extension
        val fileType = getInputType(ext)
        LOG.debug("SopsRunner: Decrypting $filePath (type=$fileType)")

        return if (fileType == "binary") {
            runSops(
                listOf(
                    "--decrypt",
                    "--input-type", "binary",
                    "--output-type", "binary",
                    filePath
                ),
                filePath
            )
        } else {
            runSops(listOf("--decrypt", filePath), filePath)
        }
    }

    /**
     * Encrypt a file and return the encrypted content.
     * Automatically detects file type from extension.
     */
    fun encrypt(filePath: String): String {
        val ext = File(filePath).extension
        val fileType = getInputType(ext)
        LOG.debug("SopsRunner: Encrypting $filePath (type=$fileType)")

        return if (fileType == "binary") {
            runSops(
                listOf(
                    "--encrypt",
                    "--input-type", "binary",
                    "--output-type", "binary",
                    filePath
                ),
                filePath
            )
        } else {
            runSops(listOf("--encrypt", filePath), filePath)
        }
    }

    /**
     * Encrypt in-memory content for a logical file path.
     *
     * Stages the payload in a secure per-invocation temp file and passes
     * --filename-override so SOPS resolves creation_rules and infers the
     * input/output format from the *original* path, not the temp path.
     * This avoids the Windows-incompatible /dev/stdin trick that an
     * earlier stdin-based version relied on, while still satisfying the
     * rule-matching requirement that motivated dropping the previous
     * temp-file approach.
     */
    fun encryptContent(content: String, filePath: String): String {
        LOG.debug("SopsRunner: Encrypting content for $filePath")

        val tempPath = SecureTempFiles.create("sopsie-enc-", ".payload", content)
        return try {
            runCommand(
                settings.sopsPath,
                listOf(
                    "--encrypt",
                    "--filename-override", filePath,
                    tempPath.toString()
                ),
                getWorkingDirectory(filePath),
                null,
                settings.timeout
            )
        } finally {
            try {
                Files.deleteIfExists(tempPath)
            } catch (e: Exception) {
                LOG.warn("Failed to clean up encrypt temp file $tempPath: ${e.message}")
            }
        }
    }

    /**
     * Update keys in an encrypted file based on .sops.yaml.
     * Re-encrypts the file with the keys defined in the matching creation rule.
     */
    fun updateKeys(filePath: String) {
        LOG.debug("SopsRunner: Updating keys for $filePath")
        runSops(listOf("updatekeys", "--yes", filePath), filePath)
    }

    /**
     * Rotate the data key used to encrypt the file.
     * Decrypts and re-encrypts all values with a new data key.
     */
    fun rotate(filePath: String) {
        LOG.debug("SopsRunner: Rotating data key for $filePath")
        runSops(listOf("rotate", "--in-place", filePath), filePath)
    }

    /**
     * Check if SOPS CLI is available
     */
    fun checkCliAvailable(): Boolean {
        return try {
            runCommand(settings.sopsPath, listOf("--version"), File("."), null, 5000)
            true
        } catch (e: Exception) {
            LOG.debug("SOPS CLI check failed: ${e.message}")
            false
        }
    }

    /**
     * Get SOPS version
     */
    fun getVersion(): String? {
        return try {
            runCommand(settings.sopsPath, listOf("--version"), File("."), null, 5000).trim()
        } catch (e: Exception) {
            LOG.debug("Failed to get SOPS version: ${e.message}")
            null
        }
    }

    private fun runSops(args: List<String>, filePath: String): String {
        return runCommand(
            settings.sopsPath,
            args,
            getWorkingDirectory(filePath),
            null,
            settings.timeout
        )
    }

    private fun runCommand(
        cmd: String,
        args: List<String>,
        workDir: File,
        stdin: String?,
        timeout: Int
    ): String {
        val commandLine = GeneralCommandLine()
            .withExePath(cmd)
            .withParameters(args)
            .withWorkDirectory(workDir)
            .withCharset(StandardCharsets.UTF_8)
            .withEnvironment("SOPS_DISABLE_VERSION_CHECK", "1")

        val handler = CapturingProcessHandler(commandLine)

        // Write stdin on a dedicated thread so stdout/stderr readers can
        // drain concurrently. Writing stdin synchronously before
        // runProcess deadlocks once the payload exceeds the OS pipe
        // buffer, because no reader is consuming stdout/stderr yet.
        val stdinWriter: Thread? = if (stdin != null) {
            val bytes = stdin.toByteArray(StandardCharsets.UTF_8)
            Thread({
                try {
                    handler.processInput.use { it.write(bytes) }
                } catch (ex: Exception) {
                    LOG.debug("stdin write failed: ${ex.message}")
                }
            }, "sopsie-stdin-writer").apply {
                isDaemon = true
                start()
            }
        } else {
            null
        }

        val output: ProcessOutput = handler.runProcess(timeout)
        stdinWriter?.join(1000)

        if (output.isTimeout) {
            throw SopsException(SopsError.timeout(timeout))
        }

        if (output.exitCode == 0) {
            return output.stdout
        }

        throw SopsException(parseError(output.stderr, output.exitCode))
    }

    internal fun parseError(stderr: String, code: Int): SopsError {
        val lowerStderr = stderr.lowercase()

        if (lowerStderr.contains("could not decrypt") ||
            lowerStderr.contains("failed to get the data key") ||
            lowerStderr.contains("cannot find key")
        ) {
            return SopsError.keyAccessDenied(stderr)
        }

        if (lowerStderr.contains("config file not found") || lowerStderr.contains(".sops.yaml")) {
            return SopsError.configNotFound(stderr)
        }

        if (lowerStderr.contains("error parsing") || lowerStderr.contains("yaml:")) {
            return SopsError.invalidFile(stderr)
        }

        if (stderr.contains("encrypt")) {
            return SopsError.encryptionFailed(stderr)
        }

        if (stderr.contains("decrypt")) {
            return SopsError.decryptionFailed(stderr)
        }

        return SopsError.unknown(stderr, code)
    }

    companion object {
        @JvmStatic
        fun getInstance(): SopsRunner = service()
    }
}
