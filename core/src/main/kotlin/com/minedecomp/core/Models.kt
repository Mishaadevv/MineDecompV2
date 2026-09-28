package com.minedecomp.core

enum class JarType { CLIENT, SERVER, BOTH }

fun JarType.sides(): List<JarType> = when (this) {
    JarType.BOTH -> listOf(JarType.CLIENT, JarType.SERVER)
    else -> listOf(this)
}

fun JarType.dirName(): String = when (this) {
    JarType.CLIENT -> "client"
    JarType.SERVER -> "server"
    JarType.BOTH -> throw IllegalArgumentException("BOTH must be split into sides first")
}

data class VersionInfo(
    val id: String,
    val type: String,
    val url: String,
    val releaseTime: String
)

data class VersionManifest(
    val versions: List<VersionInfo>
)

data class VersionMetadata(
    val id: String,
    val downloads: Map<String, DownloadInfo>
)

data class DownloadInfo(
    val url: String,
    val sha1: String,
    val size: Long
)

data class Mappings(
    val version: String,
    val jarType: JarType,
    val classMappings: Map<String, String>,
    val methodMappings: Map<String, String>,
    val fieldMappings: Map<String, String>
)

data class DecompRequest(
    val version: String,
    val jarType: JarType,
    val outputDir: String,
    val cacheDir: String,
    val generateGradle: Boolean = false,
    val decompiler: DecompilerType = DecompilerType.VINEFLOWER
)

enum class DecompilerType { VINEFLOWER, CFR }

data class DecompResult(
    val success: Boolean,
    val classesDecompiled: Int,
    val classesWithErrors: List<String>,
    val outputDir: String,
    val errors: List<String>
)

sealed class PipelineEvent {
    data class StageStarted(val stage: String) : PipelineEvent()
    data class StageProgress(val stage: String, val current: Int, val total: Int) : PipelineEvent()
    data class StageCompleted(val stage: String) : PipelineEvent()
    data class Log(val level: LogLevel, val message: String) : PipelineEvent()
    data class Completed(val result: DecompResult) : PipelineEvent()
    data class Failed(val error: String) : PipelineEvent()
}

enum class LogLevel { INFO, WARN, ERROR }

interface PipelineCallbacks {
    fun onEvent(event: PipelineEvent)
}
