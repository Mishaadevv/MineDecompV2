package com.minedecomp.core.mappings

import com.google.gson.Gson
import com.minedecomp.core.JarType
import com.minedecomp.core.Mappings
import com.minedecomp.core.VersionManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fallback provider for versions with no published mappings (e.g. pre-1.6.4
 * releases). Returns empty mappings, so the pipeline still runs but the
 * output keeps obfuscated (notch) names: a, b, func_..., etc.
 *
 * Must be registered LAST in the provider chain: every other provider takes
 * precedence over it.
 */
class NoopMappingsProvider(private val cacheDir: String) : MappingProvider {
    override val name = "Obfuscated (no mappings)"
    override val hasMappings = false

    companion object {
        private const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override suspend fun supports(version: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val manifestFile = File(cacheDir, "version_manifest_v2.json")
            if (!manifestFile.exists()) {
                val request = Request.Builder().url(MANIFEST_URL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    manifestFile.parentFile?.mkdirs()
                    manifestFile.writeText(response.body?.string() ?: return@withContext false)
                }
            }
            val manifest = gson.fromJson(manifestFile.readText(), VersionManifest::class.java)
            manifest.versions.any { it.id == version && it.type == "release" }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun fetchMappings(version: String, jarType: JarType): Mappings {
        return Mappings(version, jarType, emptyMap(), emptyMap(), emptyMap())
    }
}
