package com.minedecomp.core.pipeline

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MappingSourceTest {

    @Test
    fun `auto normalizes to null`() {
        assertNull(DecompPipeline.normalizeMappingSource("auto"))
        assertNull(DecompPipeline.normalizeMappingSource(" AUTO "))
    }

    @Test
    fun `aliases resolve to canonical provider names`() {
        assertEquals("MCPConfig", DecompPipeline.normalizeMappingSource("mcp"))
        assertEquals("Mojang Official", DecompPipeline.normalizeMappingSource("mojang"))
        assertEquals("Mojang Official", DecompPipeline.normalizeMappingSource("official"))
        assertEquals("Yarn (Fabric)", DecompPipeline.normalizeMappingSource("yarn"))
        assertEquals("MCPConfig 1.13", DecompPipeline.normalizeMappingSource("mcpnew"))
        assertEquals("Obfuscated (no mappings)", DecompPipeline.normalizeMappingSource("noop"))
    }

    @Test
    fun `canonical names pass through`() {
        assertEquals("Yarn (Fabric)", DecompPipeline.normalizeMappingSource("Yarn (Fabric)"))
        assertEquals("Mojang Official", DecompPipeline.normalizeMappingSource("Mojang Official"))
    }
}
