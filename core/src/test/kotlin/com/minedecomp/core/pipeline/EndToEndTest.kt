package com.minedecomp.core.pipeline

import com.minedecomp.core.DecompRequest
import com.minedecomp.core.DecompilerType
import com.minedecomp.core.JarType
import com.minedecomp.core.PipelineCallbacks
import com.minedecomp.core.PipelineEvent
import com.minedecomp.core.cache.CacheManager
import com.minedecomp.mappings.mcpconfig.McpConfigProvider
import com.minedecomp.mappings.mojang.MojangMappingsProvider
import com.minedecomp.mappings.yarn.YarnMappingsProvider
import com.minedecomp.mappings.mcpnew.McpNewProvider
import com.minedecomp.core.mappings.NoopMappingsProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Full end-to-end test: Mojang download -> mappings -> ASM remap ->
 * Vineflower decompile -> layout. The provider chain is the same as the app.
 * Version comes from -De2eVersion (default 1.7.10); the key-class assertion
 * from -De2eKeyFile/-De2eKeySnippet (defaults fit MCP versions).
 * Run: gradlew :core:test --tests "*EndToEndTest*" -De2eVersion=1.14.3 -De2eKeyFile=MinecraftClient.java -De2eKeySnippet="class MinecraftClient"
 */
class EndToEndTest {

    @Test
    fun `full pipeline produces java sources`() = runBlocking {
        val version = System.getProperty("e2eVersion", "1.7.10")
        val keyFile = System.getProperty("e2eKeyFile", "Minecraft.java")
        val keySnippet = System.getProperty("e2eKeySnippet", "class Minecraft")
        val minClasses = System.getProperty("e2eMinClasses", "1000").toInt()
        val workDir = File("build/e2e/$version").apply { mkdirs() }
        val cacheDir = File("build/e2e-cache").apply { mkdirs() }

        val cacheManager = CacheManager(cacheDir.absolutePath)
        val pipeline = DecompPipeline(
            cacheManager,
            listOf(
                McpConfigProvider(cacheDir.absolutePath),
                MojangMappingsProvider(cacheDir.absolutePath),
                YarnMappingsProvider(cacheDir.absolutePath),
                McpNewProvider(cacheDir.absolutePath),
                NoopMappingsProvider(cacheDir.absolutePath)
            )
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
        assertTrue(result.classesDecompiled > minClasses, "too few classes: ${result.classesDecompiled}")

        val sourcesDir = File(workDir, "sources/$version/client")
        assertTrue(sourcesDir.isDirectory, "sources dir missing: $sourcesDir")

        val javaFiles = sourcesDir.walkTopDown().filter { it.isFile && it.extension == "java" }.toList()
        println("E2E java files on disk: ${javaFiles.size}")
        assertTrue(javaFiles.size > minClasses, "too few .java files: ${javaFiles.size}")

        // Key class must exist with a readable name and package
        val keyClass = javaFiles.firstOrNull { it.name == keyFile }
        assertTrue(keyClass != null, "$keyFile not found")
        println("E2E $keyFile at: ${keyClass.relativeTo(sourcesDir)}")
        val content = keyClass.readText()
        assertTrue(content.contains(keySnippet), "$keyFile has no '$keySnippet' declaration")
        println("E2E $keyFile first 500 chars:")
        println(content.take(500))
    }
}
