package com.minedecomp.mappings.mcpnew

import com.minedecomp.core.JarType
import com.minedecomp.core.Mappings
import com.minedecomp.core.mappings.MappingProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Mappings provider for the 1.13-1.14.3 hole left between classic MCP
 * (joined.srg ends at 1.12.2) and Mojang official mappings (start at
 * 1.14.4): Forge MCPConfig `joined.tsrg` (notch -> searge) plus
 * `mcp_snapshot` CSVs (searge -> human-readable MCP names).
 *
 * In practice Yarn takes precedence for 1.14-1.14.3 (nicer names), so this
 * provider mostly serves 1.13-1.13.2. Versions without a snapshot CSV fall
 * back to searge names — still far more useful than obfuscated output.
 */
class McpNewProvider(private val cacheDir: String) : MappingProvider {
    override val name = "MCPConfig 1.13"

    companion object {
        private const val MAVEN_BASE = "https://files.minecraftforge.net/maven/de/oceanlabs/mcp"
        private const val SNAPSHOT_META = "$MAVEN_BASE/mcp_snapshot/maven-metadata.xml"

        /**
         * MC versions with a published MCPConfig joined.tsrg.
         * Verified against files.minecraftforge.net.
         */
        private val tsrgVersions: Set<String> = setOf(
            "1.13", "1.13.1", "1.13.2",
            "1.14", "1.14.1", "1.14.2", "1.14.3"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun supports(version: String): Boolean {
        return version in tsrgVersions
    }

    override suspend fun fetchMappings(version: String, jarType: JarType): Mappings = withContext(Dispatchers.IO) {
        if (version !in tsrgVersions) {
            throw RuntimeException("No MCPConfig mappings published for version $version")
        }
        val mappingsDir = File(cacheDir, "mappings/mcpnew-$version")
        mappingsDir.mkdirs()

        // Stage 1: notch -> searge from joined.tsrg inside mcp_config.
        val tsrgFile = File(mappingsDir, "joined.tsrg")
        if (!tsrgFile.exists()) {
            downloadZipEntry(
                url = "$MAVEN_BASE/mcp_config/$version/mcp_config-$version.zip",
                entrySuffix = "joined.tsrg",
                dest = tsrgFile
            )
        }
        val (classMap, seargeMethods, seargeFields) = parseTsrg(tsrgFile.readText())

        // Stage 2: searge -> MCP names from the newest mcp_snapshot CSVs.
        // No stable channel exists for these versions; without a snapshot
        // the searge names stand on their own.
        val methodNames = mutableMapOf<String, String>()
        val fieldNames = mutableMapOf<String, String>()
        val snapshot = newestSnapshot(version)
        if (snapshot != null) {
            val methodsCsv = File(mappingsDir, "methods.csv")
            val fieldsCsv = File(mappingsDir, "fields.csv")
            if (!methodsCsv.exists() || !fieldsCsv.exists()) {
                val snapUrl = "$MAVEN_BASE/mcp_snapshot/$snapshot/mcp_snapshot-$snapshot.zip"
                downloadZipEntry(snapUrl, "methods.csv", methodsCsv)
                downloadZipEntry(snapUrl, "fields.csv", fieldsCsv)
            }
            parseCsv(methodsCsv.readText(), methodNames)
            parseCsv(fieldsCsv.readText(), fieldNames)
        }

        val methodMappings = mutableMapOf<String, String>()
        val fieldMappings = mutableMapOf<String, String>()
        for ((key, searge) in seargeMethods) {
            methodMappings[key] = methodNames[searge] ?: searge
        }
        for ((key, searge) in seargeFields) {
            fieldMappings[key] = fieldNames[searge] ?: searge
        }

        Mappings(version, jarType, classMap, methodMappings, fieldMappings)
    }

    /** Newest mcp_snapshot id for an MC version (ids start with YYYYMMDD). */
    private fun newestSnapshot(version: String): String? {
        val metaFile = File(cacheDir, "mappings/mcpnew-snapshots.xml")
        val xml = if (metaFile.exists() && isFresh(metaFile)) {
            metaFile.readText()
        } else {
            val request = Request.Builder().url(SNAPSHOT_META).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                metaFile.parentFile?.mkdirs()
                metaFile.writeText(body)
                body
            }
        }
        return Regex("<version>([^<]+)</version>").findAll(xml)
            .map { it.groupValues[1] }
            .filter { it.endsWith("-$version") }
            .maxOrNull()
    }

    private fun isFresh(file: File): Boolean {
        val age = System.currentTimeMillis() - file.lastModified()
        return age < 24 * 3600 * 1000L
    }

    private fun downloadZipEntry(url: String, entrySuffix: String, dest: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to download mappings: ${response.code} ($url)")
            }
            val body = response.body ?: throw RuntimeException("Empty response ($url)")
            ZipInputStream(body.byteStream()).use { zis ->
                var entry = zis.nextEntry
                var found = false
                while (entry != null) {
                    if (entry.name.endsWith(entrySuffix)) {
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { output -> zis.copyTo(output) }
                        found = true
                        break
                    }
                    entry = zis.nextEntry
                }
                if (!found) throw RuntimeException("Entry $entrySuffix not found in $url")
            }
        }
    }

    /**
     * Parses MCPConfig joined.tsrg:
     *   notchClass seargeClass
     *       notchMember notchDesc seargeMember   (methods: desc starts with '(')
     *       notchField seargeField
     * `tsrg2` header lines (newer files) are skipped.
     */
    internal fun parseTsrg(content: String): Triple<Map<String, String>, Map<String, String>, Map<String, String>> {
        val classMap = mutableMapOf<String, String>()
        val methods = mutableMapOf<String, String>()
        val fields = mutableMapOf<String, String>()
        var currentNotchOwner: String? = null

        content.lines().forEach { raw ->
            if (raw.isBlank()) return@forEach
            if (raw.startsWith("#") || raw.startsWith("tsrg2")) return@forEach
            if (raw[0] == '\t' || raw[0] == ' ') {
                val owner = currentNotchOwner ?: return@forEach
                val parts = raw.trim().split(Regex("\\s+"))
                if (parts.size < 2) return@forEach
                val notchName = parts[0]
                if (parts.size >= 3 && parts[1].startsWith("(")) {
                    methods["$owner.$notchName"] = parts[2]
                } else {
                    fields["$owner.$notchName"] = parts.last()
                }
                return@forEach
            }
            val parts = raw.trim().split(Regex("\\s+"))
            if (parts.size >= 2) {
                currentNotchOwner = parts[0]
                classMap[parts[0]] = parts[1]
            }
        }
        return Triple(classMap, methods, fields)
    }

    /**
     * Parses mcp snapshot CSV: header "searge,name,side,desc", rows
     * "searge,mcpName,...".
     */
    private fun parseCsv(content: String, out: MutableMap<String, String>) {
        content.lines().forEach { line ->
            if (line.isBlank() || line.startsWith("searge,")) return@forEach
            val idx = line.indexOf(',')
            if (idx <= 0) return@forEach
            val searge = line.substring(0, idx).trim()
            val rest = line.substring(idx + 1)
            val nameEnd = rest.indexOf(',')
            val name = if (nameEnd < 0) rest.trim() else rest.substring(0, nameEnd).trim()
            if (searge.isNotEmpty() && name.isNotEmpty()) {
                out[searge] = name
            }
        }
    }
}
