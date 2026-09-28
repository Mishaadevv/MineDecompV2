package com.minedecomp.ui.screens

import com.minedecomp.app.AppSettings
import com.minedecomp.core.DecompilerType
import com.minedecomp.core.JarType
import com.minedecomp.core.cache.CacheManager
import com.minedecomp.core.mappings.MappingProvider
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.geometry.Insets
import javafx.geometry.Pos
import kotlinx.coroutines.*

class MainView(
    private val settings: AppSettings,
    private val mappingProviders: List<MappingProvider>,
    onStartDecompile: (String, JarType, String, String, Boolean, DecompilerType) -> Unit,
    onOpenSettings: () -> Unit
) : VBox(20.0) {

    data class VersionRow(val id: String, val provider: String?)

    private val allRows = FXCollections.observableArrayList<VersionRow>()
    private val filteredRows = FilteredList(allRows)
    private val versionList = ListView<VersionRow>()
    private val searchField = TextField()
    private val jarTypeToggle = ToggleGroup()
    private val startButton = Button("Start Decompilation")
    private val settingsButton = Button("Settings")
    private val statusLabel = Label("Loading versions...")

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        padding = Insets(40.0)
        alignment = Pos.CENTER
        styleClass.add("main-view")

        val title = Label("MineDecompV2")
        title.font = Font.font("System", FontWeight.BOLD, 36.0)
        title.styleClass.add("app-title")

        val subtitle = Label("Minecraft decompiler for modding")
        subtitle.font = Font.font("System", 16.0)
        subtitle.styleClass.add("app-subtitle")

        val versionLabel = Label("Minecraft version:")
        versionLabel.styleClass.add("field-label")

        searchField.promptText = "Search versions..."
        searchField.prefWidth = 340.0
        searchField.textProperty().addListener { _, _, query ->
            val q = query.trim().lowercase()
            filteredRows.setPredicate { row -> q.isEmpty() || row.id.lowercase().contains(q) }
        }

        versionList.items = filteredRows
        versionList.prefWidth = 340.0
        versionList.prefHeight = 260.0
        versionList.styleClass.add("version-list")
        versionList.setCellFactory {
            object : ListCell<VersionRow>() {
                override fun updateItem(item: VersionRow?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        textFill = Color.web("#e0e0e0")
                    } else if (item.provider != null) {
                        text = "${item.id}  —  ${item.provider}"
                        textFill = Color.web("#e0e0e0")
                    } else {
                        text = "${item.id}  —  no mappings"
                        textFill = Color.web("#616161")
                    }
                }
            }
        }
        versionList.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            updateStartState(selected)
        }

        val jarTypeLabel = Label("JAR type:")
        jarTypeLabel.styleClass.add("field-label")

        val clientRadio = RadioButton("Client")
        val serverRadio = RadioButton("Server")
        val bothRadio = RadioButton("Both")

        clientRadio.toggleGroup = jarTypeToggle
        serverRadio.toggleGroup = jarTypeToggle
        bothRadio.toggleGroup = jarTypeToggle
        clientRadio.isSelected = true

        val jarTypeBox = HBox(15.0, clientRadio, serverRadio, bothRadio)
        jarTypeBox.alignment = Pos.CENTER

        startButton.styleClass.add("primary-button")
        startButton.isDisable = true
        startButton.setOnAction {
            val selected = versionList.selectionModel.selectedItem ?: return@setOnAction
            if (selected.provider == null) return@setOnAction
            val jarType = when (jarTypeToggle.selectedToggle) {
                serverRadio -> JarType.SERVER
                bothRadio -> JarType.BOTH
                else -> JarType.CLIENT
            }

            onStartDecompile(
                selected.id,
                jarType,
                settings.outputDir,
                settings.cacheDir,
                settings.generateGradle,
                settings.decompiler
            )
        }

        settingsButton.styleClass.add("secondary-button")
        settingsButton.setOnAction { onOpenSettings() }

        val buttonBox = HBox(15.0, startButton, settingsButton)
        buttonBox.alignment = Pos.CENTER

        statusLabel.styleClass.add("status-label")

        children.addAll(
            title,
            subtitle,
            Region().apply { prefHeight = 10.0 },
            versionLabel,
            searchField,
            versionList,
            jarTypeLabel,
            jarTypeBox,
            Region().apply { prefHeight = 10.0 },
            buttonBox,
            statusLabel
        )

        loadVersions()
    }

    private fun updateStartState(selected: VersionRow?) {
        when {
            selected == null -> {
                startButton.isDisable = true
            }
            selected.provider == null -> {
                startButton.isDisable = true
                statusLabel.text = "No mappings published for ${selected.id} — decompilation unavailable."
                statusLabel.textFill = Color.ORANGE
            }
            else -> {
                startButton.isDisable = false
                statusLabel.text = "${selected.id} — mappings: ${selected.provider}."
                statusLabel.textFill = Color.web("#4CAF50")
            }
        }
    }

    private fun loadVersions() {
        scope.launch {
            try {
                val cacheManager = CacheManager(settings.cacheDir)
                val manifest = cacheManager.getVersionManifest()

                val releases = manifest.versions
                    .filter { it.type == "release" }
                    .map { it.id }

                // Resolve the mappings provider for every release in parallel.
                // Version JSONs are cached on disk, so repeat visits are instant.
                val rows = releases.map { id ->
                    async {
                        val provider = mappingProviders.firstOrNull {
                            try {
                                it.supports(id)
                            } catch (e: Exception) {
                                false
                            }
                        }
                        VersionRow(id, provider?.name)
                    }
                }.awaitAll()

                val supported = rows.count { it.provider != null }
                Platform.runLater {
                    allRows.setAll(rows)
                    val preselect = rows.firstOrNull { it.id == "1.12.2" && it.provider != null }
                        ?: rows.firstOrNull { it.provider != null }
                    versionList.selectionModel.select(preselect)
                    statusLabel.text = "${rows.size} releases, $supported with mappings."
                    statusLabel.textFill = Color.web("#4CAF50")
                }
            } catch (e: Exception) {
                Platform.runLater {
                    statusLabel.text = "Failed to load versions: ${e.message}"
                    statusLabel.textFill = Color.RED
                }
            }
        }
    }
}
