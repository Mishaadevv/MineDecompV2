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
    fun `fetch returns empty identity mappings`() = runBlocking {
        val provider = NoopMappingsProvider(tempDir.absolutePath)
        val mappings = provider.fetchMappings("1.5.2", JarType.CLIENT)

        assertTrue(mappings.classMappings.isEmpty())
        assertTrue(mappings.methodMappings.isEmpty())
        assertTrue(mappings.fieldMappings.isEmpty())
    }
}
