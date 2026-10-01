package com.minedecomp.core.mappings

import com.minedecomp.core.JarType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoopMappingsProviderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `supports any release from the manifest`() = runBlocking {
        val provider = NoopMappingsProvider(tempDir.absolutePath)
        assertTrue(provider.supports("1.5.2"))
        assertTrue(provider.supports("1.0"))
        assertTrue(provider.supports("1.12.2"))
        assertFalse(provider.hasMappings)
    }

    @Test
    fun `supports pre-1_0 era and new-scheme releases via obfuscated fallback`() = runBlocking {
        val provider = NoopMappingsProvider(tempDir.absolutePath)
        // Beta / Alpha / Classic (old_beta / old_alpha in the manifest)
        assertTrue(provider.supports("b1.7.3"))
        assertTrue(provider.supports("a1.2.6"))
        assertTrue(provider.supports("c0.0.13a"))
        // New year-based scheme: no official mappings, obfuscated fallback
        assertTrue(provider.supports("26.3"))
        assertFalse(provider.hasMappings)
    }

    @Test
    fun `fetch returns empty identity mappings`() = runBlocking {
        val provider = NoopMappingsProvider(tempDir.absolutePath)
        val mappings = provider.fetchMappings("1.5.2", JarType.CLIENT)

        assertTrue(mappings.classMappings.isEmpty())
        assertTrue(mappings.methodMappings.isEmpty())
        assertTrue(mappings.fieldMappings.isEmpty())
    }
}
