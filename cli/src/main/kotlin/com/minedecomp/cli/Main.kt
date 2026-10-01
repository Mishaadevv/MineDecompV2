package com.minedecomp.cli

import com.minedecomp.app.AppSettings
import com.minedecomp.app.DecompService
import com.minedecomp.app.ProgressBus
import com.minedecomp.core.DecompRequest
import com.minedecomp.core.DecompilerType
import com.minedecomp.core.JarType
import com.minedecomp.core.LogLevel
import com.minedecomp.core.PipelineEvent
import com.minedecomp.core.cache.CacheManager
import com.minedecomp.core.mappings.MappingProvider
import com.minedecomp.mappings.mcpconfig.McpConfigProvider
import com.minedecomp.mappings.mojang.MojangMappingsProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val USAGE = """
MineDecompV2 CLI — decompile Minecraft without the GUI.

Usage:
  minedecomp --version <id> [--side client|server|both] [--output <dir>]
             [--cache <dir>] [--decompiler vineflower|cfr] [--gradle]
  minedecomp --list-versions [--snapshots]
  minedecomp --help

Examples:
  minedecomp --version 1.12.2
  minedecomp --version 1.7.10 --side both --decompiler cfr
  minedecomp --version 1.20.1 --output ./out --gradle
""".trimIndent()

fun main(args: Array<String>) {
    val opts = parseArgs(args)

    if (opts.help) {
        println(USAGE)
        return
    }

    runBlocking {
        val cacheDir = opts.cache ?: AppSettings.defaultCacheDir()
        val providers: List<MappingProvider> = listOf(
            McpConfigProvider(cacheDir),
            MojangMappingsProvider(cacheDir),
            com.minedecomp.mappings.yarn.YarnMappingsProvider(cacheDir),
            com.minedecomp.mappings.mcpnew.McpNewProvider(cacheDir),
            com.minedecomp.core.mappings.NoopMappingsProvider(cacheDir)
        )

        if (opts.listVersions) {
            listVersions(cacheDir, providers, opts.snapshots)
            return@runBlocking
        }

        val version = opts.version
        if (version == null) {
            System.err.println("Missing required option: --version <id>\n\n$USAGE")
            kotlin.system.exitProcess(2)
        }

        val service = DecompService(
            cacheDir = cacheDir,
            outputDir = opts.output ?: AppSettings.defaultOutputDir(),
            mappingProviders = providers,
            progressBus = ProgressBus()
        )
        runDecompilation(service, version, opts)
        service.shutdown()
    }
}

private data class Opts(
    val help: Boolean = false,
    val listVersions: Boolean = false,
    val snapshots: Boolean = false,
    val version: String? = null,
    val side: String = "client",
    val output: String? = null,
    val cache: String? = null,
    val decompiler: String = "vineflower",
    val gradle: Boolean = false
)

private fun parseArgs(args: Array<String>): Opts {
    var o = Opts()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--help", "-h" -> o = o.copy(help = true)
            "--list-versions" -> o = o.copy(listVersions = true)
            "--snapshots" -> o = o.copy(snapshots = true)
            "--version" -> {
                o = o.copy(version = args.getOrNull(++i) ?: fail("Missing value for --version"))
            }
            "--side" -> {
                val side = args.getOrNull(++i)?.lowercase() ?: fail("Missing value for --side")
                if (side !in setOf("client", "server", "both")) fail("Invalid --side: $side")
                o = o.copy(side = side)
            }
            "--output" -> o = o.copy(output = args.getOrNull(++i) ?: fail("Missing value for --output"))
            "--cache" -> o = o.copy(cache = args.getOrNull(++i) ?: fail("Missing value for --cache"))
            "--decompiler" -> {
                val d = args.getOrNull(++i)?.lowercase() ?: fail("Missing value for --decompiler")
                if (d !in setOf("vineflower", "cfr")) fail("Invalid --decompiler: $d")
                o = o.copy(decompiler = d)
            }
            "--gradle" -> o = o.copy(gradle = true)
            else -> fail("Unknown option: ${args[i]}\n\n$USAGE")
        }
        i++
    }
    return o
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    kotlin.system.exitProcess(2)
    throw IllegalStateException(message)
}

private suspend fun listVersions(cacheDir: String, providers: List<MappingProvider>, snapshots: Boolean) {
    val manifest = CacheManager(cacheDir).getVersionManifest()
    // Releases + pre-1.0 era (old_beta / old_alpha). Snapshots only with
    // --snapshots (700+ noisy entries) but decompile the same way.
    val playable = manifest.versions.filter {
        it.type == "release" || it.type == "old_beta" || it.type == "old_alpha" ||
            (snapshots && it.type == "snapshot")
    }
    for (entry in playable) {
        val provider = providers.firstOrNull {
            try {
                it.supports(entry.id)
            } catch (e: Exception) {
                false
            }
        }
        val tag = if (entry.type == "release") "" else " [${entry.type}]"
        println("${entry.id}$tag  —  ${provider?.name ?: "no mappings"}")
    }
}

private suspend fun runDecompilation(service: DecompService, version: String, opts: Opts) {
    val done = CompletableDeferred<Int>()
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    scope.launch {
        service.progressBus.events.collect { event ->
            when (event) {
                is PipelineEvent.StageStarted -> println("==> ${event.stage}")
                is PipelineEvent.StageProgress ->
                    println("    ${event.stage}: ${event.current}/${event.total}")
                is PipelineEvent.StageCompleted -> println("<== ${event.stage} done")
                is PipelineEvent.Log -> {
                    val prefix = when (event.level) {
                        LogLevel.INFO -> "INFO"
                        LogLevel.WARN -> "WARN"
                        LogLevel.ERROR -> "ERROR"
                    }
                    println("[$prefix] ${event.message}")
                }
                is PipelineEvent.Completed -> {
                    val r = event.result
                    println()
                    println("Result: success=${r.success}, classes=${r.classesDecompiled}, " +
                        "errors=${r.classesWithErrors.size}, output=${r.outputDir}")
                    if (r.errors.isNotEmpty()) {
                        r.errors.forEach { println("ERROR: $it") }
                    }
                    done.complete(if (r.success) 0 else 1)
                }
                is PipelineEvent.Failed -> {
                    println("FAILED: ${event.error}")
                    done.complete(1)
                }
            }
        }
    }

    service.startDecompilation(
        DecompRequest(
            version = version,
            jarType = when (opts.side) {
                "server" -> JarType.SERVER
                "both" -> JarType.BOTH
                else -> JarType.CLIENT
            },
            outputDir = opts.output ?: AppSettings.defaultOutputDir(),
            cacheDir = opts.cache ?: AppSettings.defaultCacheDir(),
            generateGradle = opts.gradle,
            decompiler = if (opts.decompiler == "cfr") DecompilerType.CFR else DecompilerType.VINEFLOWER
        )
    )

    val code = done.await()
    scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    if (code != 0) kotlin.system.exitProcess(code)
}
