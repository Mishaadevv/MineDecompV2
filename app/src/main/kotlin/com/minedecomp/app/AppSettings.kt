package com.minedecomp.app

import com.minedecomp.core.DecompilerType
import java.util.prefs.Preferences

/**
 * Centralized application settings backed by java.util.prefs.Preferences.
 * Both the main screen and the settings screen read/write through this class,
 * so user choices are actually applied to the decompilation pipeline.
 */
class AppSettings {
    private val prefs = Preferences.userNodeForPackage(AppSettings::class.java)

    var cacheDir: String
        get() = prefs.get("cacheDir", defaultCacheDir())
        set(value) = prefs.put("cacheDir", value)

    var outputDir: String
        get() = prefs.get("outputDir", defaultOutputDir())
        set(value) = prefs.put("outputDir", value)

    var decompiler: DecompilerType
        get() = try {
            DecompilerType.valueOf(prefs.get("decompiler", DecompilerType.VINEFLOWER.name))
        } catch (e: IllegalArgumentException) {
            DecompilerType.VINEFLOWER
        }
        set(value) = prefs.put("decompiler", value.name)

    var generateGradle: Boolean
        get() = prefs.getBoolean("generateGradle", false)
        set(value) = prefs.putBoolean("generateGradle", value)

    var cleanupTempFiles: Boolean
        get() = prefs.getBoolean("cleanup", true)
        set(value) = prefs.putBoolean("cleanup", value)

    var darkTheme: Boolean
        get() = prefs.get("theme", "dark") == "dark"
        set(value) = prefs.put("theme", if (value) "dark" else "light")

    companion object {
        fun defaultCacheDir(): String = System.getProperty("user.home") + "/.minedecomp/cache"
        fun defaultOutputDir(): String = System.getProperty("user.home") + "/.minedecomp/output"
    }
}
