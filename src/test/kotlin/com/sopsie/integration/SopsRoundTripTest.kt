package com.sopsie.integration

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sopsie.detection.SopsDetector
import com.sopsie.execution.SopsRunner
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * End-to-end integration suite. Invokes the real SOPS CLI with a real age
 * key and exercises encrypt → decrypt round-trips through [SopsRunner].
 *
 * Boots a headless IntelliJ environment so the application-level services
 * ([SopsRunner], [SopsDetector], [com.sopsie.services.SopsSettingsService])
 * are wired up exactly as they are in production. Lives in a separate
 * gradle source set so it can be CI-gated and skipped when `sops` isn't
 * on PATH.
 *
 * The age key + .sops.yaml fixture live under src/integrationTest/resources/
 * and are pointed at via the `SOPS_AGE_KEY_FILE` environment variable set
 * by the gradle `integrationTest` task.
 */
class SopsRoundTripTest : BasePlatformTestCase() {

    private lateinit var workDir: Path
    private val createdDirs = mutableListOf<Path>()
    private var sopsMissing = false

    override fun setUp() {
        super.setUp()
        if (!sopsAvailable()) {
            sopsMissing = true
            return
        }
        workDir = Files.createTempDirectory("sopsie-it-")
        createdDirs.add(workDir)
        // Drop the .sops.yaml fixture into the working directory so SOPS
        // discovers it via its standard parent-directory walk.
        copyResource("/.sops.yaml.integration", workDir.resolve(".sops.yaml"))
    }

    private fun sopsAvailable(): Boolean = try {
        ProcessBuilder("sops", "--version")
            .redirectErrorStream(true)
            .start()
            .also { it.waitFor() }
            .exitValue() == 0
    } catch (_: Exception) {
        false
    }

    private fun skipIfNoSops(): Boolean {
        if (sopsMissing) {
            println("[SopsRoundTripTest] Skipping: sops is not on PATH.")
            return true
        }
        return false
    }

    override fun tearDown() {
        try {
            createdDirs.forEach { dir ->
                runCatching {
                    Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .forEach { Files.deleteIfExists(it) }
                }
            }
            createdDirs.clear()
        } finally {
            super.tearDown()
        }
    }

    fun `test SopsRunner encrypt produces ciphertext that SopsDetector recognises`() {
        if (skipIfNoSops()) return
        val plain = workDir.resolve("plain.yaml")
        Files.writeString(plain, "api_key: hunter2\ndb_password: swordfish\n")

        val cipher = SopsRunner.getInstance().encrypt(plain.toString())

        assertTrue("ciphertext should mention sops", cipher.contains("sops:"))
        assertTrue(
            "detector should classify the ciphertext as encrypted",
            SopsDetector.getInstance().isContentEncrypted(cipher)
        )
        assertFalse(
            "ciphertext must not contain the plaintext value",
            cipher.contains("hunter2") || cipher.contains("swordfish")
        )
    }

    fun `test encrypt then decrypt round-trips back to the original plaintext`() {
        if (skipIfNoSops()) return
        val plain = workDir.resolve("roundtrip.yaml")
        val original = "api_key: hunter2\nnested:\n    secret: swordfish\n"
        Files.writeString(plain, original)

        // Encrypt the on-disk file (writes ciphertext to stdout, captured)
        val cipher = SopsRunner.getInstance().encrypt(plain.toString())
        Files.writeString(plain, cipher)

        val decrypted = SopsRunner.getInstance().decrypt(plain.toString())
        assertEquals(original, decrypted)
    }

    fun `test encryptContent matches the rules via filename-override`() {
        if (skipIfNoSops()) return
        // No file on disk for the source; encryptContent stages a temp
        // file internally and passes --filename-override so SOPS picks
        // up rules from .sops.yaml in the working directory of the
        // logical file path.
        val syntheticPath = workDir.resolve("via-stdin.yaml").toString()
        val cipher = SopsRunner.getInstance()
            .encryptContent("api_key: hunter2\n", syntheticPath)

        assertTrue(cipher.contains("sops:"))
        assertTrue(SopsDetector.getInstance().isContentEncrypted(cipher))
    }

    fun `test decrypt of a non-encrypted file fails with a SopsException`() {
        if (skipIfNoSops()) return
        val plain = workDir.resolve("not-encrypted.yaml")
        Files.writeString(plain, "api_key: hunter2\n")

        try {
            SopsRunner.getInstance().decrypt(plain.toString())
            fail("decrypt should have thrown for a non-encrypted file")
        } catch (ex: com.sopsie.model.SopsException) {
            // Expected — sops returns a non-zero exit code, runCommand maps to SopsException.
            assertNotNull(ex.error)
        }
    }

    private fun copyResource(resourcePath: String, target: Path) {
        val stream = javaClass.getResourceAsStream(resourcePath)
            ?: error("missing test resource: $resourcePath")
        stream.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
    }
}
