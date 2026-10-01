package com.minedecomp.mappings.mojang

import com.google.gson.Gson
import com.minedecomp.core.JarType
import com.minedecomp.core.Mappings
import com.minedecomp.core.mappings.MappingProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Mappings provider based on the official Mojang mappings (ProGuard format)
 * published in the version manifest for 1.14.4+ (1.14.4-1.21.11 verified).
 * New-scheme releases (26.x, ...) are probed factually as well: if Mojang
 * publishes mappings for them, they are picked up automatically.
 * Files are downloaded from Mojang's own servers (piston-data), exactly like
 * the official Minecraft Launcher does - nothing is bundled with the app.
 */
class MojangMappingsProvider(private val cacheDir: String) : MappingProvider {
    override val name = "Mojang Official"

    companion object {
        private const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override suspend fun supports(version: String): Boolean = withContext(Dispatchers.IO) {
        if (!isInRange(version)) return@withContext false
        try {
            mappingsUrl(version, JarType.CLIENT) != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun fetchMappings(version: String, jarType: JarType): Mappings = withContext(Dispatchers.IO) {
        val url = mappingsUrl(version, jarType)
            ?: throw RuntimeException("No official Mojang mappings published for version $version")

        val mappingsDir = File(cacheDir, "mappings/mojang-$version")
        mappingsDir.mkdirs()
        val fileName = if (jarType == JarType.CLIENT) "client.txt" else "server.txt"
        val mappingsFile = File(mappingsDir, fileName)
        if (!mappingsFile.exists()) {
            download(url, mappingsFile)
        }

        val (classMap, methods, fields) = parseProGuard(mappingsFile.readText())
        Mappings(version, jarType, classMap, methods, fields)
    }

    /**
     * Pre-filter to avoid a network round-trip for versions that never had
     * official mappings (1.0-1.13.x, alpha/beta classics). The final decision
     * is factual (presence of client/server mappings in the version JSON),
     * so new-scheme releases (26.x, ...) automatically light up if Mojang
     * publishes mappings for them later — currently they have none and fall
     * back to the obfuscated provider.
     */
    private fun isInRange(version: String): Boolean {
        // Classic 1.x numbering: official mappings exist for 1.14+.
        if (version.startsWith("1.")) {
            val minor = version.split(".").getOrNull(1)
                ?.substringBefore("-")?.toIntOrNull() ?: return false
            return minor >= 14
        }
        // New year-based scheme (26.1, 26.2, 26.3, ...) and its snapshots
        // (26.4-snapshot-1, ...): let the factual manifest check decide.
        // Old pre-1.0 ids (b1.7.3, a1.2.6, c0.0.13a, rd-132211, ...) are not
        // numeric and never have official mappings.
        val major = version.substringBefore(".").substringBefore("-").toIntOrNull()
            ?: return false
        return major >= 20
    }

    private fun mappingsUrl(version: String, jarType: JarType): String? {
        val manifestFile = File(cacheDir, "version_manifest_v2.json")
        if (!manifestFile.exists()) {
            download(MANIFEST_URL, manifestFile)
        }
        val manifest = gson.fromJson(manifestFile.readText(), MojangManifest::class.java)
        val entry = manifest.versions.find { it.id == version } ?: return null

        val versionFile = File(cacheDir, "versions/$version.json")
        if (!versionFile.exists()) {
            download(entry.url, versionFile)
        }
        val meta = gson.fromJson(versionFile.readText(), VersionMeta::class.java)
        val downloads = meta.downloads ?: return null
        val info = if (jarType == JarType.CLIENT) downloads.client_mappings else downloads.server_mappings
        return info?.url
    }

    private fun download(url: String, dest: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("Failed to download mappings: ${response.code} ($url)")
            val body = response.body ?: throw RuntimeException("Empty response ($url)")
            dest.parentFile?.mkdirs()
            body.byteStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    /**
     * Parses Mojang ProGuard mappings:
     *   official.dotted.Class -> notch:
     *       type officialField -> notchField
     *       [line:line:]retType officialMethod(args) -> notchMethod
     * Member keys are "notchOwner.notchName" ( notch descriptors are dropped,
     * mirroring the SRG behavior used for older versions).
     */
    private fun parseProGuard(content: String): Triple<Map<String, String>, Map<String, String>, Map<String, String>> {
        val classMap = mutableMapOf<String, String>()
        val methods = mutableMapOf<String, String>()
        val fields = mutableMapOf<String, String>()
        var currentNotchOwner: String? = null

        content.lines().forEach { raw ->
            if (raw.isBlank() || raw.startsWith("#")) return@forEach
            if (raw[0] == ' ' || raw[0] == '\t') {
                val owner = currentNotchOwner ?: return@forEach
                var line = raw.trim().replaceFirst(Regex("^\\d+:\\d+:"), "")
                val arrow = line.lastIndexOf("->")
                if (arrow < 0) return@forEach
                val notchName = line.substring(arrow + 2).trim()
                if (notchName.isEmpty() || notchName == "<init>" || notchName == "<clinit>") return@forEach
                val left = line.substring(0, arrow).trim()
                if (left.contains("(")) {
                    // method: retType officialName(args)
                    val paren = left.indexOf("(")
                    val beforeParen = left.substring(0, paren).trim()
                    val officialName = beforeParen.substringAfterLast(" ").substringAfterLast(".")
                    if (officialName.isNotEmpty()) {
                        methods["$owner.$notchName"] = officialName
                    }
                } else {
                    // field: type officialName
                    val officialName = left.substringAfterLast(" ").substringAfterLast(".")
                    if (officialName.isNotEmpty()) {
                        fields["$owner.$notchName"] = officialName
                    }
                }
            } else {
                val arrow = raw.lastIndexOf("->")
                if (arrow < 0) return@forEach
                val official = raw.substring(0, arrow).trim().replace(".", "/")
                val notch = raw.substring(arrow + 2).trim().trimEnd(':')
                if (official.isNotEmpty() && notch.isNotEmpty()) {
                    currentNotchOwner = notch
                    classMap[notch] = official
                }
            }
        }
        return Triple(classMap, methods, fields)
    }

    private data class MojangManifest(val versions: List<MojangVersionEntry>)
    private data class MojangVersionEntry(val id: String, val url: String)
    private data class VersionMeta(val downloads: VersionDownloads?)
    private data class VersionDownloads(
        val client_mappings: ArtifactInfo?,
        val server_mappings: ArtifactInfo?
    )
    private data class ArtifactInfo(val url: String)
}
