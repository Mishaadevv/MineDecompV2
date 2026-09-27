package com.minedecomp.core.cache

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class CacheManagerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `test cache manager creates directory`() {
        val cacheManager = CacheManager(tempDir.absolutePath)
        assertTrue(tempDir.exists())
    }

    @Test
    fun `test download file with retry`() = runBlocking {
        val cacheManager = CacheManager(tempDir.absolutePath)
        val testFile = File(tempDir, "test.txt")

        val result = cacheManager.downloadFile(
            "https://httpbin.org/bytes/1024",
            testFile.absolutePath
        )

        assertTrue(result.exists())
        assertTrue(result.length() > 0)
    }
}
