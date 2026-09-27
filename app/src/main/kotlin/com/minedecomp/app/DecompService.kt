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
    private val progressBus: ProgressBus
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

            pipeline.execute(request, callbacks)
        }
    }

    fun cancel() {
        currentJob?.cancel()
        progressBus.emit(PipelineEvent.Log(LogLevel.WARN, "Decompilation cancelled by user"))
    }

    fun shutdown() {
        scope.cancel()
    }
}
