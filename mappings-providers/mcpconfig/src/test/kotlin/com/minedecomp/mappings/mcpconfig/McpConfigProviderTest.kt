package com.minedecomp.mappings.mcpconfig

import com.minedecomp.core.JarType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpConfigProviderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `supports known versions`() = runBlocking {
        val provider = McpConfigProvider(tempDir.absolutePath)
        // Full MCP range with joined.srg
        for (v in listOf(
            "1.6.4", "1.7.2", "1.7.10",
            "1.8", "1.8.8", "1.8.9",
            "1.9", "1.9.2", "1.9.4",
            "1.10", "1.10.2",
            "1.11", "1.11.1", "1.11.2",
            "1.12", "1.12.1", "1.12.2"
        )) {
            assertTrue(provider.supports(v), "should support $v")
        }
        // No srg published for these
        for (v in listOf("1.10.1", "1.9.1", "1.8.1", "1.7.9", "1.6.2", "1.5.2", "1.13.2", "1.16.5", "1.20.1")) {
            assertFalse(provider.supports(v), "should not support $v")
        }
    }

    @Test
    fun `fetch mappings for 1_12_2`() = runBlocking {
        val provider = McpConfigProvider(tempDir.absolutePath)
        val mappings = provider.fetchMappings("1.12.2", JarType.CLIENT)

        assertTrue(mappings.classMappings.isNotEmpty(), "class mappings should not be empty")
        assertTrue(mappings.methodMappings.isNotEmpty(), "method mappings should not be empty")
        assertTrue(mappings.fieldMappings.isNotEmpty(), "field mappings should not be empty")

        // Spot check: notch class remapped to a readable name
        assertTrue(
            mappings.classMappings.containsValue("net/minecraft/util/text/TextFormatting"),
            "TextFormatting should be remapped"
        )
        // Spot check: at least one human-readable (non-searge) method name present
        val humanNames = mappings.methodMappings.values.filter {
            !it.startsWith("func_") && !it.startsWith("m_") && it.length > 1
        }
        assertTrue(humanNames.isNotEmpty(), "should contain MCP (human-readable) method names")
    }

    @Test
    fun `fetch mappings for 1_7_10`() = runBlocking {
        val provider = McpConfigProvider(tempDir.absolutePath)
        val mappings = provider.fetchMappings("1.7.10", JarType.CLIENT)

        assertTrue(mappings.classMappings.isNotEmpty(), "class mappings should not be empty")
        assertTrue(mappings.methodMappings.isNotEmpty(), "method mappings should not be empty")
        assertTrue(mappings.fieldMappings.isNotEmpty(), "field mappings should not be empty")

        // Spot check: notch class 'a' remapped to EnumChatFormatting in 1.7.10
        assertTrue(
            mappings.classMappings.containsValue("net/minecraft/util/EnumChatFormatting"),
            "EnumChatFormatting should be remapped"
        )
    }

    @Test
    fun `fetch mappings for 1_11_1`() = runBlocking {
        val provider = McpConfigProvider(tempDir.absolutePath)
        val mappings = provider.fetchMappings("1.11.1", JarType.CLIENT)

        assertTrue(mappings.classMappings.isNotEmpty(), "class mappings should not be empty")
        assertTrue(mappings.methodMappings.isNotEmpty(), "method mappings should not be empty")
        assertTrue(mappings.fieldMappings.isNotEmpty(), "field mappings should not be empty")

        val humanNames = mappings.methodMappings.values.filter {
            !it.startsWith("func_") && it.length > 1
        }
        assertTrue(humanNames.isNotEmpty(), "should contain MCP (human-readable) method names")
    }

    @Test
    fun `fetch srg-only mappings for 1_7_2`() = runBlocking {
        val provider = McpConfigProvider(tempDir.absolutePath)
        val mappings = provider.fetchMappings("1.7.2", JarType.CLIENT)

        assertTrue(mappings.classMappings.isNotEmpty(), "class mappings should not be empty")
        assertTrue(mappings.methodMappings.isNotEmpty(), "method mappings should not be empty")
        assertTrue(mappings.fieldMappings.isNotEmpty(), "field mappings should not be empty")

        // No stable CSV exists for 1.7.2: searge names are the fallback
        assertTrue(
            mappings.methodMappings.values.any { it.startsWith("func_") },
            "should fall back to searge names"
        )
    }
}
