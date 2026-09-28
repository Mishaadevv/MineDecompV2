package com.minedecomp.app

import com.minedecomp.core.*
import com.minedecomp.core.cache.CacheManager
import com.minedecomp.core.mappings.MappingProvider
import com.minedecomp.core.pipeline.DecompPipeline
import kotlinx.coroutines.*
import java.io.File

class DecompService(
    private val cacheDir: String,
    private val outputDir: String,
    private val mappingProviders: List<MappingProvider>,
    val progressBus: ProgressBus
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentJob: Job? = null

    fun startDecompilation(request: DecompRequest) {
        currentJob = scope.launch {
            val cacheManager = CacheManager(cacheDir)
            val pipeline = DecompPipeline(cacheManager, mappingProviders)

            val callbacks = object : PipelineCallbacks {
                override fun onEvent(event: PipelineEvent) {
                    progressBus.emit(event)
                }
            }

            val sides = request.jarType.sides()
            if (sides.size == 1) {
                pipeline.execute(request, callbacks)
            } else {
                runBoth(request, pipeline, callbacks)
            }
        }
    }

    /**
     * Runs CLIENT then SERVER sequentially on the shared event bus and emits
     * one combined result pointing at the common version folder.
     */
    private suspend fun runBoth(
        request: DecompRequest,
        pipeline: DecompPipeline,
        callbacks: PipelineCallbacks
    ) {
        val results = mutableListOf<DecompResult>()
        // Swallow per-side terminal events: only the combined result below
        // may switch the UI to the result screen.
        val sideCallbacks = object : PipelineCallbacks {
            override fun onEvent(event: PipelineEvent) {
                when (event) {
                    is PipelineEvent.Completed, is PipelineEvent.Failed -> Unit
                    else -> callbacks.onEvent(event)
                }
            }
        }
        for (side in request.jarType.sides()) {
            callbacks.onEvent(
                PipelineEvent.Log(LogLevel.INFO, "=== Starting ${side.dirName()} side ===")
            )
            results.add(pipeline.execute(request.copy(jarType = side), sideCallbacks))
            if (currentJob?.isCancelled == true) break
        }

        val combined = DecompResult(
            success = results.isNotEmpty() && results.all { it.success },
            classesDecompiled = results.sumOf { it.classesDecompiled },
            classesWithErrors = results.flatMap { r ->
                r.classesWithErrors.map { "[${r.outputDir.substringAfterLast(File.separator)}] $it" }
            },
            outputDir = File(request.outputDir, "sources/${request.version}").absolutePath,
            errors = results.flatMap { it.errors }
        )
        callbacks.onEvent(PipelineEvent.Completed(combined))
    }

    fun cancel() {
        currentJob?.cancel()
        progressBus.emit(PipelineEvent.Log(LogLevel.WARN, "Decompilation cancelled by user"))
    }

    fun shutdown() {
        scope.cancel()
    }
}
