package com.sopsie.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SopsConfigParserTest {

    @Test fun `parses single rule with path_regex`() {
        val config = SopsConfigParser.parse(
            """
            creation_rules:
              - path_regex: secrets/.*\.yaml${'$'}
                age: age1test
            """.trimIndent()
        )
        assertEquals(1, config.creationRules.size)
        assertEquals("""secrets/.*\.yaml${'$'}""", config.creationRules[0].pathRegex)
        assertEquals("age1test", config.creationRules[0].age)
    }

    @Test fun `parses multiple rules preserving order`() {
        val config = SopsConfigParser.parse(
            """
            creation_rules:
              - filename_regex: .*\.secret\.json${'$'}
                age: age1one
              - path_regex: secrets/.*
                age: age1two
              - age: age1catchall
            """.trimIndent()
        )
        assertEquals(3, config.creationRules.size)
        assertEquals(""".*\.secret\.json${'$'}""", config.creationRules[0].filenameRegex)
        assertEquals("secrets/.*", config.creationRules[1].pathRegex)
        assertNull(config.creationRules[2].pathRegex)
        assertNull(config.creationRules[2].filenameRegex)
    }

    @Test fun `parses key_groups with shamir_threshold`() {
        val config = SopsConfigParser.parse(
            """
            creation_rules:
              - path_regex: .*
                shamir_threshold: 2
                key_groups:
                  - age:
                      - age1a
                      - age1b
                    pgp:
                      - DEADBEEF
            """.trimIndent()
        )
        val rule = config.creationRules[0]
        assertEquals(2, rule.shamirThreshold)
        assertNotNull(rule.keyGroups)
        assertEquals(1, rule.keyGroups!!.size)
        assertEquals(listOf("age1a", "age1b"), rule.keyGroups!![0].age)
        assertEquals(listOf("DEADBEEF"), rule.keyGroups!![0].pgp)
    }

    @Test fun `throws on empty content`() {
        expectParseError("") { it.contains("Empty or invalid", ignoreCase = true) }
    }

    @Test fun `throws when creation_rules is missing`() {
        expectParseError("destination_rules: []\n") {
            it.contains("creation_rules")
        }
    }

    @Test fun `throws when creation_rules is not a list`() {
        expectParseError("creation_rules: not-a-list\n") {
            it.contains("must be an array")
        }
    }

    @Test fun `throws on non-object rule entry`() {
        expectParseError(
            """
            creation_rules:
              - "just a string"
            """.trimIndent()
        ) {
            it.contains("creation_rules[0]") && it.contains("must be an object")
        }
    }

    @Test fun `throws on invalid path_regex`() {
        expectParseError(
            """
            creation_rules:
              - path_regex: "([unclosed"
                age: age1test
            """.trimIndent()
        ) {
            it.contains("path_regex is invalid")
        }
    }

    @Test fun `throws on malformed YAML`() {
        expectParseError(
            """
            creation_rules:
              - path_regex: "unclosed
                age: age1test
            """.trimIndent()
        ) {
            it.contains("Invalid YAML", ignoreCase = true)
        }
    }

    private fun expectParseError(content: String, messageCheck: (String) -> Boolean) {
        try {
            SopsConfigParser.parse(content)
            fail("expected SopsConfigParseException")
        } catch (e: SopsConfigParseException) {
            assertTrue("unexpected message: ${e.message}", messageCheck(e.message ?: ""))
        }
    }
}
