package com.minedecomp.mappings.mcpconfig

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
 * Mappings provider based on the official MCP artifacts hosted on
 * files.minecraftforge.net (the same source ForgeGradle uses).
 *
 * Two-stage remapping, works for every supported version:
 *  1. notch -> searge  (joined.srg from mcp-{version}-srg.zip)
 *  2. searge -> mcp    (methods.csv / fields.csv from mcp_stable-{stable}-{mc}.zip)
 */
class McpConfigProvider(private val cacheDir: String) : MappingProvider {
    override val name = "MCPConfig"

    companion object {
        private const val MAVEN_BASE = "https://files.minecraftforge.net/maven/de/oceanlabs/mcp"

        /**
         * MC version -> (mcp_stable number, mc short version used in the artifact path).
         * Verified against files.minecraftforge.net.
         */
        private val stableVersions: Map<String, Pair<Int, String>> = mapOf(
            "1.7.10" to (12 to "1.7.10"),
            "1.8" to (15 to "1.8"),
            "1.8.8" to (18 to "1.8"),
            "1.8.9" to (18 to "1.8"),
            "1.9" to (24 to "1.9"),
            "1.9.4" to (24 to "1.9"),
            "1.10" to (29 to "1.10.2"),
            "1.10.2" to (29 to "1.10.2"),
            "1.11" to (31 to "1.11"),
            "1.11.2" to (32 to "1.11"),
            "1.12" to (39 to "1.12"),
            "1.12.1" to (39 to "1.12"),
            "1.12.2" to (39 to "1.12")
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun supports(version: String): Boolean {
        return version in stableVersions
    }

    override suspend fun fetchMappings(version: String, jarType: JarType): Mappings = withContext(Dispatchers.IO) {
        val mappingsDir = File(cacheDir, "mappings/$version")
        mappingsDir.mkdirs()

        // Stage 1: notch -> searge from joined.srg (available for every version)
        val srgFile = File(mappingsDir, "joined.srg")
        if (!srgFile.exists()) {
            downloadZipEntry(
                url = "$MAVEN_BASE/mcp/$version/mcp-$version-srg.zip",
                entrySuffix = "joined.srg",
                dest = srgFile
            )
        }
        val (classMap, seargeMethods, seargeFields) = parseSrg(srgFile.readText())

        // Stage 2: searge -> mcp names from mcp_stable CSVs
        val methodNames = mutableMapOf<String, String>()
        val fieldNames = mutableMapOf<String, String>()
        val (stable, short) = stableVersions[version]
            ?: throw RuntimeException("No mcp_stable mapping known for version $version")
        val methodsCsv = File(mappingsDir, "methods.csv")
        val fieldsCsv = File(mappingsDir, "fields.csv")
        if (!methodsCsv.exists() || !fieldsCsv.exists()) {
            val stableUrl = "$MAVEN_BASE/mcp_stable/$stable-$short/mcp_stable-$stable-$short.zip"
            downloadZipEntry(stableUrl, "methods.csv", methodsCsv)
            downloadZipEntry(stableUrl, "fields.csv", fieldsCsv)
        }
        parseCsv(methodsCsv.readText(), methodNames)
        parseCsv(fieldsCsv.readText(), fieldNames)

        // Merge: notch -> mcp (fall back to searge when no mcp name exists)
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
     * Parses classic SRG (joined.srg) in compact slash-separated form:
     *   CL: notchClass seargeClass
     *   FD: notchOwner/notchField seargeOwner/seargeField
     *   MD: notchOwner/notchMethod notchDesc seargeOwner/seargeMethod seargeDesc
     * PK: lines (packages) are ignored - packages follow their classes.
     */
    private fun parseSrg(content: String): Triple<Map<String, String>, Map<String, String>, Map<String, String>> {
        val classMap = mutableMapOf<String, String>()
        val methods = mutableMapOf<String, String>()
        val fields = mutableMapOf<String, String>()

        content.lines().forEach { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.isEmpty()) return@forEach
            when (parts[0]) {
                "CL:" -> if (parts.size >= 3) {
                    classMap[parts[1]] = parts[2]
                }
                "FD:" -> if (parts.size >= 3) {
                    val notch = parts[1].split("/").takeIf { it.size == 2 } ?: return@forEach
                    val searge = parts[2].split("/").lastOrNull()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    fields["${notch[0]}.${notch[1]}"] = searge
                }
                "MD:" -> if (parts.size >= 4) {
                    val notch = parts[1].split("/").takeIf { it.size == 2 } ?: return@forEach
                    val searge = parts[3].split("/").lastOrNull()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    methods["${notch[0]}.${notch[1]}"] = searge
                }
            }
        }
        return Triple(classMap, methods, fields)
    }

    /**
     * Parses mcp_stable CSV: header "searge,name,side,desc", rows "searge,mcpName,...".
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
