package com.minedecomp.core.pipeline

import com.minedecomp.core.DecompRequest
import com.minedecomp.core.DecompilerType
import com.minedecomp.core.JarType
import com.minedecomp.core.PipelineCallbacks
import com.minedecomp.core.PipelineEvent
import com.minedecomp.core.cache.CacheManager
import com.minedecomp.mappings.mcpconfig.McpConfigProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Full end-to-end test: Mojang download -> MCP mappings -> ASM remap ->
 * Vineflower decompile -> layout. Version comes from -De2eVersion (default 1.7.10).
 * Run: gradlew :core:test --tests "*EndToEndTest*" -De2eVersion=1.12.2
 */
class EndToEndTest {

    @Test
    fun `full pipeline produces java sources`() = runBlocking {
        val version = System.getProperty("e2eVersion", "1.7.10")
        val workDir = File("build/e2e/$version").apply { mkdirs() }
        val cacheDir = File("build/e2e-cache").apply { mkdirs() }

        val cacheManager = CacheManager(cacheDir.absolutePath)
        val pipeline = DecompPipeline(
            cacheManager,
            listOf(McpConfigProvider(cacheDir.absolutePath))
        )

        val events = mutableListOf<PipelineEvent>()
        val callbacks = object : PipelineCallbacks {
            override fun onEvent(event: PipelineEvent) {
                events.add(event)
                if (event is PipelineEvent.Log) {
                    println("E2E [${event.level}] ${event.message}")
                }
            }
        }

        val result = pipeline.execute(
            DecompRequest(
                version = version,
                jarType = JarType.CLIENT,
                outputDir = workDir.absolutePath,
                cacheDir = cacheDir.absolutePath,
                generateGradle = false,
                decompiler = DecompilerType.VINEFLOWER
            ),
            callbacks
        )

        println("E2E RESULT: success=${result.success}, classes=${result.classesDecompiled}, errors=${result.errors}")

        assertTrue(result.success, "pipeline failed: ${result.errors}")
        assertTrue(result.classesDecompiled > 1000, "too few classes: ${result.classesDecompiled}")

        val sourcesDir = File(workDir, "sources/$version/client")
        assertTrue(sourcesDir.isDirectory, "sources dir missing: $sourcesDir")

        val javaFiles = sourcesDir.walkTopDown().filter { it.isFile && it.extension == "java" }.toList()
        println("E2E java files on disk: ${javaFiles.size}")
        assertTrue(javaFiles.size > 1000, "too few .java files: ${javaFiles.size}")

        // Key class must exist with a readable MCP name and package
        val minecraft = javaFiles.firstOrNull { it.name == "Minecraft.java" }
        assertTrue(minecraft != null, "Minecraft.java not found")
        println("E2E Minecraft.java at: ${minecraft.relativeTo(sourcesDir)}")
        val content = minecraft.readText()
        assertTrue(content.contains("class Minecraft"), "Minecraft.java has no class declaration")
        println("E2E Minecraft.java first 500 chars:")
        println(content.take(500))
    }
}
