package com.minedecomp.ui

import com.minedecomp.app.AppSettings
import com.minedecomp.app.DecompService
import com.minedecomp.app.ProgressBus
import com.minedecomp.ui.screens.MainView
import com.minedecomp.ui.screens.ResultView
import com.minedecomp.ui.screens.SettingsView
import com.minedecomp.ui.screens.ProgressView
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.stage.Stage

class MineDecompApp : Application() {

    private lateinit var progressBus: ProgressBus
    private lateinit var decompService: DecompService
    private lateinit var settings: AppSettings
    private lateinit var rootPane: StackPane

    override fun init() {
        settings = AppSettings()
        progressBus = ProgressBus()
        rebuildService()
    }

    private fun createProviders(): List<com.minedecomp.core.mappings.MappingProvider> {
        return listOf(
            com.minedecomp.mappings.mcpconfig.McpConfigProvider(settings.cacheDir, settings.mcpChannel),
            com.minedecomp.mappings.mojang.MojangMappingsProvider(settings.cacheDir),
            com.minedecomp.mappings.yarn.YarnMappingsProvider(settings.cacheDir),
            com.minedecomp.mappings.mcpnew.McpNewProvider(settings.cacheDir),
            // Fallback last: decompiles anything with obfuscated names.
            com.minedecomp.core.mappings.NoopMappingsProvider(settings.cacheDir)
        )
    }

    private fun rebuildService() {
        if (::decompService.isInitialized) {
            decompService.shutdown()
        }
        progressBus = ProgressBus()
        decompService = DecompService(
            cacheDir = settings.cacheDir,
            outputDir = settings.outputDir,
            mappingProviders = createProviders(),
            progressBus = progressBus
        )
    }

    override fun start(primaryStage: Stage) {
        rootPane = StackPane()
        rootPane.children.add(createMainView(primaryStage))

        val scene = Scene(rootPane, 1024.0, 768.0)
        scene.stylesheets.add(javaClass.getResource("/styles/main.css")?.toExternalForm())

        primaryStage.title = "MineDecompV2"
        primaryStage.scene = scene
        primaryStage.minWidth = 800.0
        primaryStage.minHeight = 600.0
        primaryStage.show()
    }

    private fun createMainView(stage: Stage): MainView {
        return MainView(
            settings = settings,
            mappingProviders = createProviders(),
            onStartDecompile = { version, jarType, outputDir, cacheDir, generateGradle, decompiler ->
                showProgressScreen(stage, version, jarType, outputDir, cacheDir, generateGradle, decompiler)
            },
            onOpenSettings = {
                showSettings(stage)
            }
        )
    }

    private fun showProgressScreen(
        stage: Stage,
        version: String,
        jarType: com.minedecomp.core.JarType,
        outputDir: String,
        cacheDir: String,
        generateGradle: Boolean,
        decompiler: com.minedecomp.core.DecompilerType
    ) {
        rebuildService()
        val bus = progressBus
        val progressView = ProgressView(
            version = version,
            progressBus = bus,
            onCancel = {
                decompService.cancel()
                backToMain(stage)
            },
            onCompleted = { result ->
                showResult(stage, result)
            }
        )

        rootPane.children.setAll(progressView)

        decompService.startDecompilation(
            com.minedecomp.core.DecompRequest(
                version = version,
                jarType = jarType,
                outputDir = outputDir,
                cacheDir = cacheDir,
                generateGradle = generateGradle,
                decompiler = decompiler,
                mappingsSource = settings.mappingsSource
            )
        )
    }

    private fun showResult(stage: Stage, result: com.minedecomp.core.DecompResult) {
        val resultView = ResultView(
            result = result,
            onBackToMain = {
                backToMain(stage)
            }
        )
        rootPane.children.setAll(resultView)
    }

    private fun showSettings(stage: Stage) {
        val settingsView = SettingsView(
            settings = settings,
            owner = stage,
            onBack = {
                rebuildService()
                backToMain(stage)
            }
        )
        rootPane.children.setAll(settingsView)
    }

    private fun backToMain(stage: Stage) {
        rootPane.children.setAll(createMainView(stage))
    }

    override fun stop() {
        decompService.shutdown()
    }
}

fun main() {
    Application.launch(MineDecompApp::class.java)
}
