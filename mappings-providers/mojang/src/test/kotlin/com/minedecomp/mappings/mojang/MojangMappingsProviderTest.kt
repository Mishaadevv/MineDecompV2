package com.minedecomp.mappings.mojang

import com.minedecomp.core.JarType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MojangMappingsProviderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `supports versions with official mappings`() = runBlocking {
        val provider = MojangMappingsProvider(tempDir.absolutePath)
        assertTrue(provider.supports("1.20.1"))
        assertTrue(provider.supports("1.16.5"))
        assertTrue(provider.supports("1.21.11"))
        assertFalse(provider.supports("1.13.2"))
        assertFalse(provider.supports("1.12.2"))
        assertFalse(provider.supports("26.3"))
    }

    @Test
    fun `fetch mappings for 1_20_1`() = runBlocking {
        val provider = MojangMappingsProvider(tempDir.absolutePath)
        val mappings = provider.fetchMappings("1.20.1", JarType.CLIENT)

        assertTrue(mappings.classMappings.isNotEmpty(), "class mappings should not be empty")
        assertTrue(mappings.methodMappings.isNotEmpty(), "method mappings should not be empty")
        assertTrue(mappings.fieldMappings.isNotEmpty(), "field mappings should not be empty")

        // Spot check: notch class remapped to official Mojang name
        assertTrue(
            mappings.classMappings.containsValue("net/minecraft/client/Minecraft"),
            "Minecraft should be remapped to its official name"
        )
    }

    @Test
    fun `fetch mappings for 1_21_11`() = runBlocking {
        val provider = MojangMappingsProvider(tempDir.absolutePath)
        val mappings = provider.fetchMappings("1.21.11", JarType.CLIENT)

        assertTrue(mappings.classMappings.isNotEmpty(), "class mappings should not be empty")
        assertTrue(mappings.methodMappings.isNotEmpty(), "method mappings should not be empty")
        assertTrue(mappings.fieldMappings.isNotEmpty(), "field mappings should not be empty")

        assertTrue(
            mappings.classMappings.containsValue("net/minecraft/client/Minecraft"),
            "Minecraft should be remapped to its official name"
        )
    }
}
