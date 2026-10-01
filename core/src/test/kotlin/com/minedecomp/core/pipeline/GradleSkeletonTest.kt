package com.minedecomp.core.pipeline

import com.minedecomp.core.JavaVersionInfo
import com.minedecomp.core.VersionLibrary
import com.minedecomp.core.VersionMetadata
import com.minedecomp.core.cache.CacheManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class GradleSkeletonTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `skeleton uses libraries toolchain and main class from metadata`() {
        val pipeline = DecompPipeline(CacheManager(tempDir.absolutePath), emptyList())
        val metadata = VersionMetadata(
            id = "1.20.1",
            downloads = emptyMap(),
            mainClass = "net.minecraft.client.main.Main",
            javaVersion = JavaVersionInfo(component = "jre", majorVersion = 17),
            libraries = listOf(
                VersionLibrary(name = "com.google.code.gson:gson:2.10.1"),
                VersionLibrary(name = "com.mojang:brigadier:1.0.18")
            )
        )
        pipeline.generateGradleProject(tempDir.absolutePath, "1.20.1", metadata)

        val build = File(tempDir, "gradle-project/build.gradle.kts").readText()
        assertTrue(build.contains("JavaLanguageVersion.of(17)"), "toolchain must come from metadata")
        assertTrue(build.contains("implementation(\"com.google.code.gson:gson:2.10.1\")"))
        assertTrue(build.contains("implementation(\"com.mojang:brigadier:1.0.18\")"))
        assertTrue(build.contains("mainClass.set(\"net.minecraft.client.main.Main\")"))
        assertTrue(
            File(tempDir, "gradle-project/settings.gradle.kts").readText().contains("minedecomp-1.20.1")
        )
    }

    @Test
    fun `skeleton falls back to java 8 without metadata details`() {
        val pipeline = DecompPipeline(CacheManager(tempDir.absolutePath), emptyList())
        pipeline.generateGradleProject(
            tempDir.absolutePath, "b1.7.3",
            VersionMetadata(id = "b1.7.3", downloads = emptyMap())
        )
        val build = File(tempDir, "gradle-project/build.gradle.kts").readText()
        assertTrue(build.contains("JavaLanguageVersion.of(8)"))
    }
}
