package com.minedecomp.mappings.mcpnew

import com.minedecomp.core.JarType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpNewProviderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `supports mcpconfig gap versions`() = runBlocking {
        val provider = McpNewProvider(tempDir.absolutePath)
        for (v in listOf("1.13", "1.13.1", "1.13.2", "1.14", "1.14.3")) {
            assertTrue(provider.supports(v), "should support $v")
        }
        for (v in listOf("1.12.2", "1.14.4", "1.16.5", "1.20.1", "1.5.2", "b1.7.3", "26.3")) {
            assertFalse(provider.supports(v), "should not support $v")
        }
    }

    @Test
    fun `parses joined tsrg rows`() {
        val provider = McpNewProvider(tempDir.absolutePath)
        val tsrg = listOf(
            "a net/minecraft/util/text/TextFormatting",
            "\ta BLACK",
            "\ta (C)La; func_211165_a"
        ).joinToString("\n")
        val (classes, methods, fields) = provider.parseTsrg(tsrg)
        assertTrue(classes["a"] == "net/minecraft/util/text/TextFormatting")
        assertTrue(fields["a.a"] == "BLACK")
        assertTrue(methods["a.a"] == "func_211165_a")
    }

    @Test
    fun `fetch mappings for 1_13_2`() = runBlocking {
        val provider = McpNewProvider(tempDir.absolutePath)
        val mappings = provider.fetchMappings("1.13.2", JarType.CLIENT)

        assertTrue(mappings.classMappings.isNotEmpty(), "class mappings should not be empty")
        assertTrue(mappings.methodMappings.isNotEmpty(), "method mappings should not be empty")
        assertTrue(mappings.fieldMappings.isNotEmpty(), "field mappings should not be empty")

        // Spot check: notch class remapped to a readable name.
        assertTrue(
            mappings.classMappings.containsValue("net/minecraft/util/text/TextFormatting"),
            "TextFormatting should be remapped"
        )
        // Spot check: at least one human-readable (non-searge) method name present.
        val humanNames = mappings.methodMappings.values.filter {
            !it.startsWith("func_") && it.length > 1
        }
        assertTrue(humanNames.isNotEmpty(), "should contain MCP (human-readable) method names")
    }
}
