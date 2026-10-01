package com.minedecomp.mappings.yarn

import com.minedecomp.core.JarType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YarnMappingsProviderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `supports versions with yarn builds`() = runBlocking {
        val provider = YarnMappingsProvider(tempDir.absolutePath)
        // The Mojang/MCP gap: no official mappings, no joined.srg.
        assertTrue(provider.supports("1.14.3"))
        assertTrue(provider.supports("1.14"))
        // Covered elsewhere (Mojang official) but yarn exists too.
        assertTrue(provider.supports("1.20.1"))
        // No yarn published: pre-1.14, new scheme, pre-1.0 era.
        assertFalse(provider.supports("1.13.2"))
        assertFalse(provider.supports("1.12.2"))
        assertFalse(provider.supports("26.3"))
        assertFalse(provider.supports("b1.7.3"))
    }

    @Test
    fun `parses tiny v1 rows`() {
        val provider = YarnMappingsProvider(tempDir.absolutePath)
        val tiny = listOf(
            "v1\tofficial\tintermediary\tnamed",
            "CLASS\ta\tnet/minecraft/class_1158\tnet/minecraft/util/math/Quaternion",
            "CLASS\taaf\$a\tnet/minecraft/class_3549\$class_3550\tnet/minecraft/util/WeightedPicker\$Entry",
            "FIELD\ta\t[F\ta\tfield_5656\tcomponents"
        ).joinToString("\n")
        val (classes, _, fields) = provider.parseTiny(tiny)
        assertTrue(classes["a"] == "net/minecraft/util/math/Quaternion")
        assertTrue(classes["aaf\$a"] == "net/minecraft/util/WeightedPicker\$Entry")
        assertTrue(fields["a.a"] == "components")
    }

    @Test
    fun `parses tiny v2 rows`() {
        val provider = YarnMappingsProvider(tempDir.absolutePath)
        val tiny = "tiny\t2\t0\tofficial\tintermediary\tnamed\n" +
            "c\ta\tnet/minecraft/class_7833\tnet/minecraft/util/math/RotationAxis\n" +
            "\tf\tLa;\ta\tfield_40713\tNEGATIVE_X\n" +
            "\tm\t(F)Lorg/joml/Quaternionf;\ta\tmethod_46349\trotate\n"
        val (classes, methods, fields) = provider.parseTiny(tiny)
        assertTrue(classes["a"] == "net/minecraft/util/math/RotationAxis")
        assertTrue(fields["a.a"] == "NEGATIVE_X")
        assertTrue(methods["a.a"] == "rotate")
    }

    @Test
    fun `fetch mappings for 1_14_3`() = runBlocking {
        val provider = YarnMappingsProvider(tempDir.absolutePath)
        val mappings = provider.fetchMappings("1.14.3", JarType.CLIENT)

        assertTrue(mappings.classMappings.isNotEmpty(), "class mappings should not be empty")
        assertTrue(mappings.methodMappings.isNotEmpty(), "method mappings should not be empty")
        assertTrue(mappings.fieldMappings.isNotEmpty(), "field mappings should not be empty")

        // Spot check: notch class remapped to a readable Yarn name.
        assertTrue(
            mappings.classMappings.containsValue("net/minecraft/client/MinecraftClient"),
            "MinecraftClient should be remapped to its Yarn name"
        )
    }
}
