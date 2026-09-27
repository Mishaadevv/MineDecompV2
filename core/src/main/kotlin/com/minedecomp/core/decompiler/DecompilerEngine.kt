package com.minedecomp.core.decompiler

import com.minedecomp.core.DecompilerType
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.jar.JarFile

class DecompilerEngine {

    fun decompile(
        inputJar: File,
        outputDir: File,
        type: DecompilerType,
        onClassDecompiled: (current: Int, total: Int) -> Unit
    ): DecompStats {
        return when (type) {
            DecompilerType.VINEFLOWER -> decompileWithVineflower(inputJar, outputDir, onClassDecompiled)
            DecompilerType.CFR -> decompileWithCFR(inputJar, outputDir, onClassDecompiled)
        }
    }

    private fun countClasses(inputJar: File): Int {
        return try {
            JarFile(inputJar).use { jar ->
                jar.entries().asSequence().count { it.name.endsWith(".class") }
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun countJavaFiles(outputDir: File): Pair<Int, List<String>> {
        if (!outputDir.isDirectory) return 0 to emptyList()
        val files = outputDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .toList()
        return files.size to emptyList()
    }

    private fun decompileWithVineflower(
        inputJar: File,
        outputDir: File,
        onClassDecompiled: (current: Int, total: Int) -> Unit
    ): DecompStats {
        val logWriter = StringWriter()
        val printWriter = PrintWriter(logWriter)

        val logger = object : IFernflowerLogger() {
            override fun writeMessage(message: String, severity: IFernflowerLogger.Severity) {
                printWriter.println("[${severity}] $message")
            }

            override fun writeMessage(message: String, severity: IFernflowerLogger.Severity, t: Throwable) {
                printWriter.println("[${severity}] $message")
                t.printStackTrace(printWriter)
            }
        }

        // Full option names via IFernflowerPreferences constants: the short CLI
        // aliases (rbr, din, ...) are only resolved by ConsoleDecompiler.main
        // argument parsing, not by the map constructor. Start from defaults.
        val options = HashMap(IFernflowerPreferences.getDefaults())
        options[IFernflowerPreferences.REMOVE_BRIDGE] = "0"
        options[IFernflowerPreferences.REMOVE_SYNTHETIC] = "0"
        options[IFernflowerPreferences.DECOMPILE_INNER] = "1"
        options[IFernflowerPreferences.DECOMPILE_CLASS_1_4] = "1"
        options[IFernflowerPreferences.DECOMPILE_ASSERTIONS] = "1"
        options[IFernflowerPreferences.HIDE_EMPTY_SUPER] = "1"
        options[IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR] = "1"
        options[IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES] = "1"
        options[IFernflowerPreferences.DECOMPILE_ENUM] = "1"
        options[IFernflowerPreferences.ASCII_STRING_CHARACTERS] = "1"
        options[IFernflowerPreferences.BOOLEAN_TRUE_ONE] = "1"
        options[IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT] = "1"
        options[IFernflowerPreferences.USE_DEBUG_VAR_NAMES] = "1"
        options[IFernflowerPreferences.REMOVE_EMPTY_RANGES] = "1"
        options[IFernflowerPreferences.FINALLY_DEINLINE] = "1"
        options[IFernflowerPreferences.MAX_PROCESSING_METHOD] = "0"
        options[IFernflowerPreferences.RENAME_ENTITIES] = "0"
        options[IFernflowerPreferences.NEW_LINE_SEPARATOR] = "1"
        options[IFernflowerPreferences.INDENT_STRING] = "    "
        options[IFernflowerPreferences.LOG_LEVEL] = "INFO"
        options[IFernflowerPreferences.THREADS] = "4"

        outputDir.mkdirs()
        val total = countClasses(inputJar)
        val errors = mutableListOf<String>()

        try {
            // ConsoleDecompiler works with whole jars: it iterates the entries itself.
            // Passing per-entry File objects pointing at non-existent paths silently
            // produces zero output, so the entire input jar is added as one source.
            // SaveType.FOLDER (DirectoryResultSaver) is required: the default legacy
            // mode repacks jar sources back into a jar instead of writing .java files.
            val decompiler = object : ConsoleDecompiler(
                outputDir, options, logger, ConsoleDecompiler.SaveType.FOLDER
            ) {}
            decompiler.addSource(inputJar)
            decompiler.decompileContext()
            decompiler.close()
        } catch (e: Exception) {
            errors.add("Decompiler error: ${e.message}")
        }

        val (produced, _) = countJavaFiles(outputDir)
        onClassDecompiled(total, total.coerceAtLeast(1))

        if (produced == 0 && errors.isEmpty()) {
            errors.add("Decompiler produced no output for ${inputJar.name} ($total classes in input)")
        }
        return DecompStats(produced, errors)
    }

    private fun decompileWithCFR(
        inputJar: File,
        outputDir: File,
        onClassDecompiled: (current: Int, total: Int) -> Unit
    ): DecompStats {
        val errors = mutableListOf<String>()
        outputDir.mkdirs()
        val total = countClasses(inputJar)

        try {
            // CFR accepts a jar path directly and decompiles every class into --outputdir.
            org.benf.cfr.reader.Main.main(
                arrayOf(
                    inputJar.absolutePath,
                    "--outputdir", outputDir.absolutePath,
                    "--silent", "true"
                )
            )
        } catch (e: Exception) {
            errors.add("CFR error: ${e.message}")
        }

        val (produced, _) = countJavaFiles(outputDir)
        onClassDecompiled(total, total.coerceAtLeast(1))

        if (produced == 0 && errors.isEmpty()) {
            errors.add("Decompiler produced no output for ${inputJar.name} ($total classes in input)")
        }
        return DecompStats(produced, errors)
    }
}

data class DecompStats(
    val classesDecompiled: Int,
    val errors: List<String>
)
