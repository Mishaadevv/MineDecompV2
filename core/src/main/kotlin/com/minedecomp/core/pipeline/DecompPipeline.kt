package com.minedecomp.core.pipeline

import com.minedecomp.core.*
import com.minedecomp.core.cache.CacheManager
import com.minedecomp.core.decompiler.DecompilerEngine
import com.minedecomp.core.mappings.MappingProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.File
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DecompPipeline(
    private val cacheManager: CacheManager,
    private val mappingProviders: List<MappingProvider>,
    private val fileLogger: FileLogger? = null
) {
    private val gson = Gson()
    private val decompilerEngine = DecompilerEngine()

    suspend fun execute(request: DecompRequest, callbacks: PipelineCallbacks): DecompResult {
        require(request.jarType != JarType.BOTH) {
            "JarType.BOTH must be split into CLIENT/SERVER runs (use DecompService)"
        }
        return try {
            callbacks.onEvent(PipelineEvent.StageStarted("download"))
            callbacks.onEvent(PipelineEvent.Log(LogLevel.INFO, "Fetching version manifest..."))
            fileLogger?.info("Fetching version manifest...")

            val manifest = cacheManager.getVersionManifest()
            val versionInfo = manifest.versions.find { it.id == request.version }
                ?: throw RuntimeException("Version ${request.version} not found in manifest")

            callbacks.onEvent(PipelineEvent.Log(LogLevel.INFO, "Found version ${versionInfo.id} (${versionInfo.type})"))

            val versionJson = cacheManager.downloadFile(
                versionInfo.url,
                "${request.cacheDir}/versions/${request.version}.json"
            )

            val metadata = gson.fromJson(versionJson.readText(), VersionMetadata::class.java)
            val downloadKey = when (request.jarType) {
                JarType.CLIENT -> "client"
                JarType.SERVER -> "server"
                JarType.BOTH -> throw IllegalArgumentException("BOTH must be split into sides first")
            }

            val downloadInfo = metadata.downloads[downloadKey]
                ?: throw RuntimeException("No $downloadKey download for version ${request.version}")

            callbacks.onEvent(PipelineEvent.Log(LogLevel.INFO, "Downloading $downloadKey.jar (${downloadInfo.size / 1024 / 1024} MB)..."))

            val jarFile = cacheManager.downloadFile(
                downloadInfo.url,
                "${request.cacheDir}/jars/${request.version}-${downloadKey}.jar",
                downloadInfo.sha1
            )

            callbacks.onEvent(PipelineEvent.StageCompleted("download"))
            fileLogger?.info("Download completed")

            callbacks.onEvent(PipelineEvent.StageStarted("mappings"))
            callbacks.onEvent(PipelineEvent.Log(LogLevel.INFO, "Fetching mappings..."))
            fileLogger?.info("Fetching mappings...")

            val provider = mappingProviders.find { it.supports(request.version) }
                ?: throw RuntimeException("No mapping provider supports version ${request.version}")

            callbacks.onEvent(PipelineEvent.Log(LogLevel.INFO, "Using mapping provider: ${provider.name}"))

            val mappings = provider.fetchMappings(request.version, request.jarType)

            callbacks.onEvent(PipelineEvent.StageCompleted("mappings"))
            fileLogger?.info("Mappings loaded")

            callbacks.onEvent(PipelineEvent.StageStarted("remap"))
            callbacks.onEvent(PipelineEvent.Log(LogLevel.INFO, "Remapping bytecode..."))
            fileLogger?.info("Remapping bytecode...")

            val remappedJar = File(request.cacheDir, "remapped/${request.version}-${downloadKey}-remapped.jar")
            remappedJar.parentFile?.mkdirs()

            remapJar(jarFile, remappedJar, mappings, callbacks)

            callbacks.onEvent(PipelineEvent.StageCompleted("remap"))
            fileLogger?.info("Remapping completed")

            callbacks.onEvent(PipelineEvent.StageStarted("decompile"))
            callbacks.onEvent(PipelineEvent.Log(LogLevel.INFO, "Decompiling with ${request.decompiler}..."))
            fileLogger?.info("Decompiling with ${request.decompiler}...")

            val outputDir = File(request.outputDir, "sources/${request.version}/${request.jarType.dirName()}")
            outputDir.mkdirs()

            val stats = decompilerEngine.decompile(
                remappedJar,
                outputDir,
                request.decompiler
            ) { current, total ->
                callbacks.onEvent(PipelineEvent.StageProgress("decompile", current, total))
            }

            callbacks.onEvent(PipelineEvent.StageCompleted("decompile"))
            fileLogger?.info("Decompilation completed: ${stats.classesDecompiled} classes, ${stats.errors.size} errors")

            callbacks.onEvent(PipelineEvent.StageStarted("layout"))
            callbacks.onEvent(PipelineEvent.Log(LogLevel.INFO, "Laying out files..."))
            fileLogger?.info("Laying out files...")

            if (request.generateGradle) {
                generateGradleProject(request.outputDir, request.version)
            }

            callbacks.onEvent(PipelineEvent.StageCompleted("layout"))
            callbacks.onEvent(PipelineEvent.StageCompleted("done"))

            val result = DecompResult(
                success = true,
                classesDecompiled = stats.classesDecompiled,
                classesWithErrors = stats.errors,
                outputDir = outputDir.absolutePath,
                errors = emptyList()
            )

            callbacks.onEvent(PipelineEvent.Completed(result))
            result

        } catch (e: Exception) {
            callbacks.onEvent(PipelineEvent.Log(LogLevel.ERROR, e.message ?: "Unknown error"))
            callbacks.onEvent(PipelineEvent.Failed(e.message ?: "Unknown error"))
            DecompResult(
                success = false,
                classesDecompiled = 0,
                classesWithErrors = emptyList(),
                outputDir = request.outputDir,
                errors = listOf(e.message ?: "Unknown error")
            )
        }
    }

    private suspend fun remapJar(
        inputJar: File,
        outputJar: File,
        mappings: Mappings,
        callbacks: PipelineCallbacks
    ) = withContext(Dispatchers.IO) {
        val total = JarFile(inputJar).use { it.entries().asSequence().count { e -> e.name.endsWith(".class") } }
        var current = 0

        JarFile(inputJar).use { jar ->
            ZipOutputStream(outputJar.outputStream()).use { zos ->
                jar.entries().asSequence().forEach { entry ->
                    if (entry.name.endsWith(".class")) {
                        val remapped = remapClass(jar.getInputStream(entry).readBytes(), mappings)
                        zos.putNextEntry(ZipEntry(renameEntry(entry.name, mappings)))
                        zos.write(remapped)
                        zos.closeEntry()

                        current++
                        callbacks.onEvent(PipelineEvent.StageProgress("remap", current, total))
                    } else {
                        zos.putNextEntry(ZipEntry(entry.name))
                        jar.getInputStream(entry).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    /**
     * Renames a jar entry using the class mappings so the remapped jar already
     * has the readable package layout (a.class -> net/minecraft/.../X.class).
     * Inner classes are mapped as a whole when present, otherwise part by part.
     */
    private fun renameEntry(entryName: String, mappings: Mappings): String {
        if (!entryName.endsWith(".class")) return entryName
        val internal = entryName.removeSuffix(".class")
        mappings.classMappings[internal]?.let { return "$it.class" }
        val renamed = internal.split("$").map { part ->
            mappings.classMappings[part] ?: part
        }.joinToString("$")
        return "$renamed.class"
    }

    private fun remapClass(bytes: ByteArray, mappings: Mappings): ByteArray {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(cr, 0)

        val remapper = object : Remapper() {
            override fun mapMethodName(owner: String, name: String, descriptor: String): String {
                val key = "$owner.$name"
                return mappings.methodMappings[key] ?: name
            }

            override fun mapFieldName(owner: String, name: String, descriptor: String): String {
                val key = "$owner.$name"
                return mappings.fieldMappings[key] ?: name
            }

            override fun map(internalName: String): String {
                return mappings.classMappings[internalName] ?: internalName
            }
        }

        val classRemapper = ClassRemapper(cw, remapper)
        cr.accept(classRemapper, 0)
        return cw.toByteArray()
    }

    private fun generateGradleProject(outputDir: String, version: String) {
        val projectDir = File(outputDir, "gradle-project")
        projectDir.mkdirs()

        File(projectDir, "build.gradle.kts").writeText("""
            plugins {
                java
                id("net.minecraftforge.gradle") version "6.0.0"
            }

            group = "com.example"
            version = "1.0.0"

            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(8))
                }
            }

            minecraft {
                mappings("official", "$version")
            }

            dependencies {
                minecraft("net.minecraft:client:$version")
            }
        """.trimIndent())

        File(projectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "minedecomp-$version"
        """.trimIndent())
    }
}
