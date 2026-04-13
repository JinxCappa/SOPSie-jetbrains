package com.sopsie.config

import com.intellij.openapi.vfs.VirtualFile
import com.sopsie.model.SopsConfig
import com.sopsie.model.SopsCreationRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Paths

class SopsRulesMatcherTest {

    private val configDir = Paths.get("/repo").toAbsolutePath()

    private fun fileAt(relPath: String): VirtualFile {
        val abs = configDir.resolve(relPath).toString()
        val name = relPath.substringAfterLast('/')
        return mock {
            on { path } doReturnPath abs
            on { this.name } doReturnPath name
        }
    }

    // Mockito-kotlin's `on {}.thenReturn` for stubbing property getters.
    private infix fun <T> org.mockito.stubbing.OngoingStubbing<T>.doReturnPath(v: T) =
        thenReturn(v)

    private fun matcher(vararg rules: SopsCreationRule): SopsRulesMatcher =
        SopsRulesMatcher(SopsConfig(creationRules = rules.toList()), configDir)

    @Before fun clearCache() = SopsRulesMatcher.clearRegexCache()

    @Test fun `matches first rule with path_regex`() {
        val m = matcher(
            SopsCreationRule(pathRegex = """secrets/.*\.yaml${'$'}""", age = "a"),
            SopsCreationRule(age = "b")
        )
        val rule = m.findMatchingRule(fileAt("secrets/prod.yaml"))
        assertEquals("a", rule?.age)
    }

    @Test fun `first-match wins even when later rule also matches`() {
        val m = matcher(
            SopsCreationRule(pathRegex = "secrets/.*", age = "first"),
            SopsCreationRule(pathRegex = """secrets/prod\.yaml""", age = "second")
        )
        assertEquals("first", m.findMatchingRule(fileAt("secrets/prod.yaml"))?.age)
    }

    @Test fun `filename_regex matches on basename only`() {
        val m = matcher(SopsCreationRule(filenameRegex = """^secret\.json${'$'}""", age = "a"))
        assertNotNull(m.findMatchingRule(fileAt("nested/deep/secret.json")))
        assertNull(m.findMatchingRule(fileAt("secret.json.backup")))
    }

    @Test fun `catch-all rule matches any file`() {
        val m = matcher(SopsCreationRule(age = "a"))
        assertNotNull(m.findMatchingRule(fileAt("anything.txt")))
        assertNotNull(m.findMatchingRule(fileAt("deep/nested/path/file")))
    }

    @Test fun `returns null when no rule matches`() {
        val m = matcher(SopsCreationRule(pathRegex = "^secrets/", age = "a"))
        assertNull(m.findMatchingRule(fileAt("public/readme.md")))
    }

    @Test fun `invalid regex is skipped without throwing`() {
        val m = matcher(
            SopsCreationRule(pathRegex = "([unclosed", age = "bad"),
            SopsCreationRule(pathRegex = """.*\.yaml${'$'}""", age = "good")
        )
        assertEquals("good", m.findMatchingRule(fileAt("x.yaml"))?.age)
    }

    @Test fun `hasMatchingRule returns true when any rule matches`() {
        val m = matcher(SopsCreationRule(pathRegex = "secrets/.*", age = "a"))
        assertTrue(m.hasMatchingRule(fileAt("secrets/x.yaml")))
    }
}
