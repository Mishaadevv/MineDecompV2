package com.minedecomp.ui.screens

import com.minedecomp.app.AppSettings
import com.minedecomp.core.DecompilerType
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.DirectoryChooser
import javafx.stage.Window
import java.io.File

class SettingsView(
    private val settings: AppSettings,
    private val owner: Window?,
    onBack: () -> Unit
) : VBox(15.0) {

    private val cacheDirField = TextField()
    private val outputDirField = TextField()
    private val decompilerCombo = ComboBox<String>()
    private val mappingsCombo = ComboBox<String>()
    private val mcpChannelCombo = ComboBox<String>()
    private val gradleCheck = CheckBox("Generate Gradle project")
    private val cleanupCheck = CheckBox("Delete temporary files after completion")
    private val themeCombo = ComboBox<String>()

    init {
        padding = Insets(30.0)
        alignment = Pos.TOP_CENTER
        styleClass.add("settings-view")

        val title = Label("Settings")
        title.font = Font.font("System", FontWeight.BOLD, 28.0)
        title.styleClass.add("screen-title")

        val backButton = Button("\u2190 Back")
        backButton.styleClass.add("secondary-button")
        backButton.setOnAction { onBack() }

        val backBox = HBox(backButton)
        backBox.alignment = Pos.TOP_LEFT

        cacheDirField.text = settings.cacheDir
        cacheDirField.prefWidth = 350.0
        outputDirField.text = settings.outputDir
        outputDirField.prefWidth = 350.0

        decompilerCombo.items.addAll("Vineflower", "CFR")
        decompilerCombo.value = when (settings.decompiler) {
            DecompilerType.VINEFLOWER -> "Vineflower"
            DecompilerType.CFR -> "CFR"
        }

        mappingsCombo.items.addAll(
            "Auto", "MCPConfig", "Mojang Official", "Yarn (Fabric)",
            "MCPConfig 1.13", "Obfuscated (no mappings)"
        )
        mappingsCombo.value = when (settings.mappingsSource.trim().lowercase()) {
            "auto", "" -> "Auto"
            "mcp", "mcpconfig" -> "MCPConfig"
            "mojang", "official", "mojang official" -> "Mojang Official"
            "yarn", "fabric", "yarn (fabric)" -> "Yarn (Fabric)"
            "mcpnew", "mcp13", "mcpconfig 1.13" -> "MCPConfig 1.13"
            "noop", "obfuscated", "none", "obfuscated (no mappings)" -> "Obfuscated (no mappings)"
            else -> "Auto"
        }

        mcpChannelCombo.items.addAll("Stable", "Snapshot")
        mcpChannelCombo.value = if (settings.mcpChannel == "snapshot") "Snapshot" else "Stable"

        gradleCheck.isSelected = settings.generateGradle
        cleanupCheck.isSelected = settings.cleanupTempFiles

        themeCombo.items.addAll("Dark", "Light")
        themeCombo.value = if (settings.darkTheme) "Dark" else "Light"

        val cacheBrowse = Button("Browse...")
        cacheBrowse.styleClass.add("secondary-button")
        cacheBrowse.setOnAction {
            pickDirectory(cacheDirField.text)?.let { cacheDirField.text = it }
        }

        val outputBrowse = Button("Browse...")
        outputBrowse.styleClass.add("secondary-button")
        outputBrowse.setOnAction {
            pickDirectory(outputDirField.text)?.let { outputDirField.text = it }
        }

        val form = GridPane().apply {
            hgap = 10.0
            vgap = 12.0
            alignment = Pos.CENTER

            add(Label("Cache directory:"), 0, 0)
            add(HBox(8.0, cacheDirField, cacheBrowse), 1, 0)
            add(Label("Output directory:"), 0, 1)
            add(HBox(8.0, outputDirField, outputBrowse), 1, 1)
            add(Label("Decompiler:"), 0, 2)
            add(decompilerCombo, 1, 2)
            add(Label("Mappings source:"), 0, 3)
            add(mappingsCombo, 1, 3)
            add(Label("MCP channel:"), 0, 4)
            add(mcpChannelCombo, 1, 4)
            add(Label("Theme:"), 0, 5)
            add(themeCombo, 1, 5)
            add(gradleCheck, 0, 6, 2, 1)
            add(cleanupCheck, 0, 7, 2, 1)
        }

        val saveButton = Button("Save")
        saveButton.styleClass.add("primary-button")
        saveButton.setOnAction {
            settings.cacheDir = cacheDirField.text.ifBlank { AppSettings.defaultCacheDir() }
            settings.outputDir = outputDirField.text.ifBlank { AppSettings.defaultOutputDir() }
            settings.decompiler = if (decompilerCombo.value == "CFR") DecompilerType.CFR else DecompilerType.VINEFLOWER
            settings.mappingsSource = if (mappingsCombo.value == "Auto") "auto" else mappingsCombo.value
            settings.mcpChannel = if (mcpChannelCombo.value == "Snapshot") "snapshot" else "stable"
            settings.generateGradle = gradleCheck.isSelected
            settings.cleanupTempFiles = cleanupCheck.isSelected
            settings.darkTheme = themeCombo.value != "Light"
            onBack()
        }

        children.addAll(backBox, title, form, saveButton)
    }

    private fun pickDirectory(initial: String): String? {
        val chooser = DirectoryChooser()
        chooser.title = "Select directory"
        val initialDir = File(initial)
        if (initialDir.isDirectory) {
            chooser.initialDirectory = initialDir
        }
        return chooser.showDialog(owner)?.absolutePath
    }
}
