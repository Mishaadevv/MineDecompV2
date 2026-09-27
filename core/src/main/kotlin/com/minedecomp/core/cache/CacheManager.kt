package com.minedecomp.core.cache

import com.google.gson.Gson
import com.minedecomp.core.VersionManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class CacheManager(private val cacheDir: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    init {
        Files.createDirectories(Paths.get(cacheDir))
    }

    suspend fun getVersionManifest(): VersionManifest = withContext(Dispatchers.IO) {
        val manifestFile = File(cacheDir, "version_manifest_v2.json")
        if (manifestFile.exists() && isFresh(manifestFile, 24)) {
            return@withContext gson.fromJson(manifestFile.readText(), VersionManifest::class.java)
        }

        val request = Request.Builder()
            .url("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
            .build()

        retryWithBackoff(3) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw RuntimeException("Failed to fetch manifest: ${response.code}")
                val body = response.body?.string() ?: throw RuntimeException("Empty manifest body")
                manifestFile.writeText(body)
                gson.fromJson(body, VersionManifest::class.java)
            }
        }
    }

    suspend fun downloadFile(url: String, destFile: String, expectedSha1: String? = null): File =
        withContext(Dispatchers.IO) {
            val file = File(destFile)
            if (file.exists() && (expectedSha1 == null || verifySha1(file, expectedSha1))) {
                return@withContext file
            }

            val request = Request.Builder().url(url).build()

            retryWithBackoff(3) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw RuntimeException("Download failed: ${response.code} for $url")
                    val body = response.body ?: throw RuntimeException("Empty response body")
                    file.parentFile?.mkdirs()
                    body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (expectedSha1 != null && !verifySha1(file, expectedSha1)) {
                        file.delete()
                        throw RuntimeException("SHA1 mismatch for $url")
                    }
                }
            }

            file
        }

    private suspend fun <T> retryWithBackoff(maxRetries: Int, block: suspend () -> T): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    delay((attempt + 1) * 2000L)
                }
            }
        }
        throw lastException ?: RuntimeException("Unknown error")
    }

    private fun isFresh(file: File, hours: Int): Boolean {
        val age = System.currentTimeMillis() - file.lastModified()
        return age < hours * 3600 * 1000
    }

    private fun verifySha1(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return hash.equals(expected, ignoreCase = true)
    }
}
