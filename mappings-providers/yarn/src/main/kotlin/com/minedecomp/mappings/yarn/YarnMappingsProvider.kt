package com.minedecomp.mappings.yarn

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
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Mappings provider based on Fabric Yarn (tiny mappings, notch -> readable).
 *
 * Covers the versions Mojang/MCP leave behind: 1.14-1.14.3 (no official
 * Mojang mappings, no joined.srg) plus any snapshot / new-scheme release
 * Fabric ever publishes Yarn builds for. A single tiny file carries the
 * whole notch -> intermediary -> named chain, so no second download is
 * needed: the `official` column is notch, the `named` column is readable.
 *
 * Old builds ship `yarn-<build>-tiny.gz` (tiny v1), new ones only
 * `yarn-<build>-mergedv2.jar` with `mappings/mappings.tiny` (tiny v2) —
 * both are tried in that order. Member keys are `owner.name` (descriptors
 * dropped), mirroring the SRG behavior used for older versions.
 */
class YarnMappingsProvider(private val cacheDir: String) : MappingProvider {
    override val name = "Yarn (Fabric)"

    companion object {
        private const val META_BASE = "https://meta.fabricmc.net/v2/versions/yarn"
        private const val MAVEN_BASE = "https://maven.fabricmc.net/net/fabricmc/yarn"
        private const val FRESHNESS_HOURS = 24
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override suspend fun supports(version: String): Boolean = withContext(Dispatchers.IO) {
        try {
            yarnBuilds(version).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun fetchMappings(version: String, jarType: JarType): Mappings = withContext(Dispatchers.IO) {
        val builds = yarnBuilds(version)
        if (builds.isEmpty()) throw RuntimeException("No Yarn builds published for version $version")
        // Newest first: meta.fabricmc.net returns builds latest-first.
        val build = builds.first().version

        val mappingsDir = File(cacheDir, "mappings/yarn-$version")
        mappingsDir.mkdirs()
        val tinyFile = File(mappingsDir, "mappings.tiny")
        if (!tinyFile.exists()) {
            val tiny = downloadTiny(build)
                ?: throw RuntimeException("No tiny mappings artifact for Yarn build $build")
            tinyFile.writeText(tiny)
        }

        val (classMap, methods, fields) = parseTiny(tinyFile.readText())
        if (classMap.isEmpty()) throw RuntimeException("Yarn mappings for $version parsed to zero classes")
        Mappings(version, jarType, classMap, methods, fields)
    }

    /** Newest-first Yarn build list for a game version, cached on disk. */
    private fun yarnBuilds(version: String): List<YarnBuild> {
        val metaDir = File(cacheDir, "mappings/yarn-meta")
        metaDir.mkdirs()
        val metaFile = File(metaDir, "$version.json")
        if (metaFile.exists() && isFresh(metaFile)) {
            return gson.fromJson(metaFile.readText(), Array<YarnBuild>::class.java)?.toList()
                ?: emptyList()
        }
        val request = Request.Builder().url("$META_BASE/$version").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 404 = no Yarn for this game version. Cache the negative so
                // the version list doesn't hammer the API on every load.
                if (response.code == 404) {
                    metaFile.writeText("[]")
                    return emptyList()
                }
                throw RuntimeException("Yarn meta lookup failed: ${response.code} ($version)")
            }
            val body = response.body?.string() ?: return emptyList()
            metaFile.writeText(body)
            return gson.fromJson(body, Array<YarnBuild>::class.java)?.toList() ?: emptyList()
        }
    }

    /** Downloads the tiny mappings text for one Yarn build, v1 gz or v2 jar. */
    private fun downloadTiny(build: String): String? {
        // Old layout: plain tiny v1 gzip next to the jar.
        val gzUrl = "$MAVEN_BASE/$build/yarn-$build-tiny.gz"
        val gzBody = getBytes(gzUrl)
        if (gzBody != null) {
            GZIPInputStream(gzBody.inputStream()).use { gis ->
                return gis.readBytes().toString(Charsets.UTF_8)
            }
        }
        // New layout: merged tiny v2 inside -mergedv2.jar.
        val jarUrl = "$MAVEN_BASE/$build/yarn-$build-mergedv2.jar"
        val jarBody = getBytes(jarUrl) ?: return null
        ZipInputStream(jarBody.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "mappings/mappings.tiny") {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    /** GET bytes, null on 404, throw on anything else. */
    private fun getBytes(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) return null
                throw RuntimeException("Download failed: ${response.code} ($url)")
            }
            return response.body?.bytes()
        }
    }

    private fun isFresh(file: File): Boolean {
        val age = System.currentTimeMillis() - file.lastModified()
        return age < FRESHNESS_HOURS * 3600 * 1000L
    }

    /**
     * Parses tiny v1 (`CLASS`/`FIELD`/`METHOD` rows, official/intermediary/
     * named columns) and tiny v2 (`c` + tabbed `m`/`f` rows). Returns
     * (classes, methods, fields) keyed exactly like the SRG providers:
     * classes by slash-separated internal name, members by `owner.name`.
     */
    internal fun parseTiny(content: String): Triple<Map<String, String>, Map<String, String>, Map<String, String>> {
        val classMap = mutableMapOf<String, String>()
        val methods = mutableMapOf<String, String>()
        val fields = mutableMapOf<String, String>()
        var currentNotchOwner: String? = null

        content.lines().forEach { raw ->
            if (raw.isBlank() || raw.startsWith("#")) return@forEach
            // Tiny v2 member rows start with a tab; tiny v1 rows never do.
            if (raw[0] == '\t' || raw[0] == ' ') {
                val owner = currentNotchOwner ?: return@forEach
                val cols = raw.trim().split('\t')
                // v2: [m|f, desc(notch), nameNotch, nameInter, nameNamed]
                if (cols.size < 4) return@forEach
                val kind = cols[0]
                val notchName = cols[2].trim()
                val named = cols.last().trim().substringAfterLast('/')
                if (notchName.isEmpty() || named.isEmpty()) return@forEach
                if (notchName == "<init>" || notchName == "<clinit>") return@forEach
                if (kind == "m") methods["$owner.$notchName"] = named
                else if (kind == "f") fields["$owner.$notchName"] = named
                return@forEach
            }
            val cols = raw.split('\t')
            when (cols[0]) {
                "v1", "tiny" -> Unit // header
                "CLASS" -> if (cols.size >= 4) {
                    // CLASS\tnotch\tintermediary\tnamed
                    val notch = cols[1].trim()
                    val named = cols[3].trim()
                    if (notch.isNotEmpty() && named.isNotEmpty()) classMap[notch] = named
                }
                "c" -> if (cols.size >= 4) {
                    // tiny v2 class row: c\tnotch\tintermediary\tnamed.
                    // Also tracks the owner for the tabbed m/f rows below.
                    val notch = cols[1].trim()
                    val named = cols[3].trim()
                    if (notch.isNotEmpty() && named.isNotEmpty()) {
                        currentNotchOwner = notch
                        classMap[notch] = named
                    }
                }
                "FIELD", "METHOD" -> if (cols.size >= 6) {
                    // tiny v1: KIND\towner\tdesc\tnameNotch\tnameInter\tnameNamed
                    val owner = cols[1].trim()
                    val notchName = cols[3].trim()
                    val named = cols[5].trim().substringAfterLast('/')
                    if (owner.isEmpty() || notchName.isEmpty() || named.isEmpty()) return@forEach
                    if (notchName == "<init>" || notchName == "<clinit>") return@forEach
                    if (cols[0] == "METHOD") methods["$owner.$notchName"] = named
                    else fields["$owner.$notchName"] = named
                }
            }
        }
        return Triple(classMap, methods, fields)
    }

    private data class YarnBuild(val version: String, val stable: Boolean)
}
