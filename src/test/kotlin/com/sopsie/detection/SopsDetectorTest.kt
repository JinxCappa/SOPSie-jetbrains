package com.sopsie.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SopsDetectorTest {

    private val detector = SopsDetector()

    private fun enc(content: String) {
        assertTrue("expected encrypted: $content", detector.isContentEncrypted(content))
    }

    private fun plain(content: String) {
        assertFalse("expected plaintext: $content", detector.isContentEncrypted(content))
    }

    @Test fun `returns false for empty content`() = plain("")

    @Test fun `returns false when content does not mention sops`() {
        plain("api_key: hunter2\ndb:\n    password: swordfish\n")
    }

    @Test fun `returns false for JSON that mentions sops in a comment`() {
        plain("""{"api_key":"hunter2","note":"mentions sops in passing"}""")
    }

    @Test fun `detects encrypted YAML with sops key and mac+version`() {
        enc(
            """
            api_key: ENC[AES256_GCM,data:abc=,iv:x=,tag:d=,type:str]
            sops:
                mac: ENC[AES256_GCM,data:aaa=]
                version: 3.9.1
            """.trimIndent()
        )
    }

    @Test fun `detects encrypted YAML with sops key and lastmodified`() {
        enc(
            """
            key: ENC[...]
            sops:
                mac: ENC[...]
                lastmodified: "2026-01-01T00:00:00Z"
            """.trimIndent()
        )
    }

    @Test fun `detects encrypted JSON`() {
        enc(
            """
            {
                "key": "ENC[...]",
                "sops": {
                    "mac": "ENC[AES256_GCM,data:aaa=]",
                    "version": "3.9.1"
                }
            }
            """.trimIndent()
        )
    }

    @Test fun `detects encrypted ENV by sops_version`() {
        enc("API_KEY=ENC[...]\nsops_version=3.9.1\nsops_mac=ENC[...]\n")
    }

    @Test fun `detects encrypted ENV by sops_mac`() {
        enc("FOO=bar\nsops_mac=ENC[...]\n")
    }

    @Test fun `detects encrypted INI with sops section`() {
        enc(
            """
            [api]
            key = ENC[...]

            [sops]
            mac = ENC[AES256_GCM,data:aaa=]
            version = 3.9.1
            """.trimIndent()
        )
    }

    @Test fun `detects SOPS binary format prefix`() {
        enc("SOPS\u0000\u0001\u0002binary-payload with sops")
    }

    @Test fun `rejects plaintext ENV mentioning sops in a comment`() {
        plain("# see sops docs\nFOO=bar\nVERSION=1.2.3\n")
    }
}
