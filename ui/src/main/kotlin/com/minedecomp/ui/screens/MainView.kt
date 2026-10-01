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

    data class VersionRow(val id: String, val type: String, val provider: String?, val hasMappings: Boolean)

    private val allRows = FXCollections.observableArrayList<VersionRow>()
    private val filteredRows = FilteredList(allRows)
    private val versionList = ListView<VersionRow>()
    private val searchField = TextField()
    private val snapshotsCheck = CheckBox("Show snapshots")
    private var showSnapshots = false
    private val clientRadio = RadioButton("Client")
    private val serverRadio = RadioButton("Server")
    private val bothRadio = RadioButton("Both")
    private var manifestUrls = mapOf<String, String>()
    private val serverAvailability = mutableMapOf<String, Boolean>()
    private val gson = com.google.gson.Gson()
    private val jarTypeToggle = ToggleGroup()
    private val startButton = Button("Start Decompilation")
    private val settingsButton = Button("Settings")
    private val statusLabel = Label("Loading versions...")

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loadJob: Job? = null

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
        versionList.prefHeight = 240.0
        versionList.styleClass.add("version-list")
        versionList.setCellFactory {
            object : ListCell<VersionRow>() {
                override fun updateItem(item: VersionRow?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        textFill = Color.web("#e0e0e0")
                    } else {
                        // Tag pre-1.0 eras so b1.7.3 / a1.2.6 don't look like typos.
                        val tag = if (item.type == "release") "" else " [${item.type}]"
                        if (item.provider != null && item.hasMappings) {
                            text = "${item.id}$tag  —  ${item.provider}"
                            textFill = Color.web("#e0e0e0")
                        } else if (item.provider != null) {
                            text = "${item.id}$tag  —  ${item.provider}"
                            textFill = Color.web("#FF9800")
                        } else {
                            text = "${item.id}$tag  —  no mappings"
                            textFill = Color.web("#616161")
                        }
                    }
                }
            }
        }
        versionList.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            updateStartState(selected)
            probeServerAvailability(selected)
        }

        val jarTypeLabel = Label("JAR type:")
        jarTypeLabel.styleClass.add("field-label")

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

        snapshotsCheck.isSelected = false
        snapshotsCheck.setOnAction {
            showSnapshots = snapshotsCheck.isSelected
            loadVersions()
        }

        children.addAll(
            title,
            subtitle,
            Region().apply { prefHeight = 10.0 },
            versionLabel,
            searchField,
            versionList,
            snapshotsCheck,
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
            !selected.hasMappings -> {
                startButton.isDisable = false
                statusLabel.text = if (selected.type == "release") {
                    "No mappings for ${selected.id} — output will keep obfuscated names."
                } else {
                    "No mappings for ${selected.id} (${selected.type}) — obfuscated output, client jar only, use Client side."
                }
                statusLabel.textFill = Color.web("#FF9800")
            }
            else -> {
                startButton.isDisable = false
                statusLabel.text = "${selected.id} — mappings: ${selected.provider}."
                statusLabel.textFill = Color.web("#4CAF50")
            }
        }
    }

    /**
     * Pre-1.6 versions publish no server jar. Probe the version JSON (once
     * per version, result cached) and lock the Server/Both sides when the
     * `server` download is absent, so the choice fails fast in the UI
     * instead of deep in the pipeline.
     */
    private fun probeServerAvailability(selected: VersionRow?) {
        serverRadio.isDisable = false
        bothRadio.isDisable = false
        serverRadio.tooltip = null
        bothRadio.tooltip = null
        if (selected == null) return
        serverAvailability[selected.id]?.let { applyServerAvailability(selected.id, it); return }
        scope.launch {
            try {
                val url = manifestUrls[selected.id] ?: return@launch
                val text = java.net.URI(url).toURL().readText()
                val meta = gson.fromJson(text, com.minedecomp.core.VersionMetadata::class.java)
                val hasServer = meta.downloads.containsKey("server")
                serverAvailability[selected.id] = hasServer
                Platform.runLater {
                    if (versionList.selectionModel.selectedItem?.id == selected.id) {
                        applyServerAvailability(selected.id, hasServer)
                    }
                }
            } catch (e: Exception) {
                // Leave sides enabled: the pipeline reports download errors.
            }
        }
    }

    private fun applyServerAvailability(versionId: String, hasServer: Boolean) {
        if (hasServer) return
        serverRadio.isDisable = true
        bothRadio.isDisable = true
        val tip = Tooltip("No server jar published for $versionId (client only)")
        serverRadio.tooltip = tip
        bothRadio.tooltip = tip
        if (jarTypeToggle.selectedToggle != clientRadio) {
            clientRadio.isSelected = true
        }
    }

    private fun loadVersions() {
        statusLabel.text = "Loading versions..."
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val cacheManager = CacheManager(settings.cacheDir)
                val manifest = cacheManager.getVersionManifest()

                // Releases + the whole pre-1.0 era (old_beta / old_alpha).
                // Snapshots only on demand: 700+ entries, each needing a
                // provider probe (cached on disk after the first load).
                val playable = manifest.versions
                    .filter {
                        it.type == "release" || it.type == "old_beta" || it.type == "old_alpha" ||
                            (showSnapshots && it.type == "snapshot")
                    }
                val urls = playable.associate { it.id to it.url }

                // Resolve the mappings provider for every version in parallel.
                // Version JSONs are cached on disk, so repeat visits are instant.
                val rows = playable.map { entry ->
                    async {
                        val provider = mappingProviders.firstOrNull {
                            try {
                                it.supports(entry.id)
                            } catch (e: Exception) {
                                false
                            }
                        }
                        VersionRow(entry.id, entry.type, provider?.name, provider?.hasMappings ?: false)
                    }
                }.awaitAll()

                val supported = rows.count { it.provider != null }
                val oldCount = rows.count { it.type != "release" }
                Platform.runLater {
                    allRows.setAll(rows)
                    manifestUrls = urls
                    val preselect = rows.firstOrNull { it.id == "1.12.2" && it.provider != null }
                        ?: rows.firstOrNull { it.provider != null }
                    versionList.selectionModel.select(preselect)
                    statusLabel.text = "${rows.size} versions ($oldCount pre-1.0), $supported with mappings."
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
