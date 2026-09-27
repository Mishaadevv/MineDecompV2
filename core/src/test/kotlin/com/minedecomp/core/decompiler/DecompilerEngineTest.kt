package com.minedecomp.core.decompiler

import com.minedecomp.core.DecompilerType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertTrue

class DecompilerEngineTest {

    @TempDir
    lateinit var tempDir: File

    private fun packSelfIntoJar(jar: File, entryName: String) {
        val bytes = DecompStats::class.java.getResourceAsStream("DecompStats.class")!!.readBytes()
        ZipOutputStream(jar.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(bytes)
            zos.closeEntry()
        }
    }

    @Test
    fun `vineflower decompiles a jar into java files`() {
        val jar = File(tempDir, "input.jar")
        packSelfIntoJar(jar, "com/minedecomp/core/decompiler/DecompStats.class")
        val out = File(tempDir, "out-vineflower")

        val engine = DecompilerEngine()
        var progress = 0 to 0
        val stats = engine.decompile(jar, out, DecompilerType.VINEFLOWER) { c, t -> progress = c to t }

        assertTrue(stats.classesDecompiled > 0, "should report produced classes, got: $stats")
        assertTrue(
            File(out, "com/minedecomp/core/decompiler/DecompStats.java").exists(),
            "decompiled .java file must exist"
        )
        assertTrue(progress.first >= 1, "progress callback must fire")
    }

    @Test
    fun `cfr decompiles a jar into java files`() {
        val jar = File(tempDir, "input.jar")
        packSelfIntoJar(jar, "com/minedecomp/core/decompiler/DecompStats.class")
        val out = File(tempDir, "out-cfr")

        val engine = DecompilerEngine()
        val stats = engine.decompile(jar, out, DecompilerType.CFR) { _, _ -> }

        assertTrue(stats.classesDecompiled > 0, "should report produced classes, got: $stats")
        assertTrue(
            File(out, "com/minedecomp/core/decompiler/DecompStats.java").exists(),
            "decompiled .java file must exist"
        )
    }
}
