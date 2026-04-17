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
import java.io.File
import java.nio.charset.StandardCharsets

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
     * Decrypt a file and return the decrypted content.
     * Format is resolved from the basename so suffixed dotenv names
     * (`.env.local`, `.env.production`) are handled correctly; SOPS's own
     * extension heuristic misses them and falls back to JSON.
     */
    fun decrypt(filePath: String): String {
        val fileType = resolveFileType(filePath)
        LOG.debug("SopsRunner: Decrypting $filePath (type=$fileType)")

        return runSops(
            listOf(
                "--decrypt",
                "--input-type", fileType,
                "--output-type", fileType,
                filePath
            ),
            filePath
        )
    }

    /**
     * Encrypt a file and return the encrypted content.
     * Format is resolved from the basename (see [decrypt]).
     */
    fun encrypt(filePath: String): String {
        val fileType = resolveFileType(filePath)
        LOG.debug("SopsRunner: Encrypting $filePath (type=$fileType)")

        return runSops(
            listOf(
                "--encrypt",
                "--input-type", fileType,
                "--output-type", fileType,
                filePath
            ),
            filePath
        )
    }

    /**
     * Encrypt content via stdin with --filename-override for rule matching.
     * Pipes plaintext to SOPS stdin — no temp file needed.
     * SOPS uses the override path for creation_rules matching and format detection.
     */
    fun encryptContent(content: String, filePath: String): String {
        LOG.debug("SopsRunner: Encrypting content for $filePath")

        val fileType = resolveFileType(filePath)
        return runCommand(
            settings.sopsPath,
            listOf(
                "--encrypt",
                "--input-type", fileType,
                "--output-type", fileType,
                "--filename-override", filePath
            ),
            getWorkingDirectory(filePath),
            content,
            settings.timeout
        )
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

        /**
         * Resolve the SOPS store type for a file path based on its basename.
         *
         * Handles the `.env.<suffix>` family (`.env.local`, `.env.production`)
         * that SOPS's own extension heuristic misses — it falls back to JSON
         * there, which chokes on `#` comments and `KEY=value` lines.
         */
        @JvmStatic
        fun resolveFileType(filePath: String): String {
            val base = File(filePath).name.lowercase()

            if (base == ".env" || base.startsWith(".env.") || base.endsWith(".env")) {
                return "dotenv"
            }

            return when (base.substringAfterLast('.', "")) {
                "json" -> "json"
                "ini" -> "ini"
                "yaml", "yml" -> "yaml"
                else -> "binary"
            }
        }
    }
}
