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
        assertTrue(provider.supports("1.12.2"))
        assertTrue(provider.supports("1.7.10"))
        assertFalse(provider.supports("1.16.5"))
        assertFalse(provider.supports("1.20.1"))
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
}
