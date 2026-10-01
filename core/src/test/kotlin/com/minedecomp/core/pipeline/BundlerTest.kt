package com.minedecomp.core.pipeline

import com.minedecomp.core.PipelineCallbacks
import com.minedecomp.core.PipelineEvent
import com.minedecomp.core.cache.CacheManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundlerTest {

    @TempDir
    lateinit var tempDir: File

    private val callbacks = object : PipelineCallbacks {
        override fun onEvent(event: PipelineEvent) = Unit
    }

    private fun pipeline(): DecompPipeline {
        return DecompPipeline(CacheManager(tempDir.absolutePath), emptyList())
    }

    private fun craftOuterJar(innerPath: String?, innerBytes: ByteArray): File {
        val outer = File(tempDir, "outer-${System.nanoTime()}.jar")
        JarOutputStream(outer.outputStream()).use { zos ->
            zos.putNextEntry(JarEntry("net/minecraft/bundler/Main.class"))
            zos.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
            zos.closeEntry()
            if (innerPath != null) {
                zos.putNextEntry(JarEntry(innerPath))
                zos.write(innerBytes)
                zos.closeEntry()
            }
        }
        return outer
    }

    @Test
    fun `finds exact versioned inner entry`() {
        val outer = craftOuterJar(
            "META-INF/versions/1.21.11/server-1.21.11.jar",
            "inner".toByteArray()
        )
        assertEquals(
            "META-INF/versions/1.21.11/server-1.21.11.jar",
            pipeline().findBundledInnerEntry(outer, "1.21.11")
        )
    }

    @Test
    fun `plain jar returns null and passes through`() {
        val outer = craftOuterJar(null, ByteArray(0))
        val p = pipeline()
        assertNull(p.findBundledInnerEntry(outer, "1.7.10"))
        assertEquals(outer, p.extractBundledJarIfNeeded(outer, "1.7.10", "client", tempDir.absolutePath, callbacks))
    }

    @Test
    fun `extracts inner jar bytes intact`() {
        val inner = "fake-game-jar-bytes".toByteArray()
        val outer = craftOuterJar("META-INF/versions/1.21.11/server-1.21.11.jar", inner)
        val extracted = pipeline().extractBundledJarIfNeeded(outer, "1.21.11", "server", tempDir.absolutePath, callbacks)
        assertTrue(extracted.exists(), "extracted jar should exist")
        assertTrue(extracted.readBytes().contentEquals(inner), "extracted bytes must match inner entry")
        // Second call reuses the cached extraction.
        assertEquals(extracted, pipeline().extractBundledJarIfNeeded(outer, "1.21.11", "server", tempDir.absolutePath, callbacks))
    }
}
