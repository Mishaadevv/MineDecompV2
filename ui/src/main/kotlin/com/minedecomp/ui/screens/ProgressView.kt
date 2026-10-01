package com.minedecomp.ui.screens

import com.minedecomp.app.ProgressBus
import com.minedecomp.core.DecompResult
import com.minedecomp.core.LogLevel
import com.minedecomp.core.PipelineEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.javafx.JavaFx
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ProgressView(
    version: String,
    progressBus: ProgressBus,
    onCancel: () -> Unit,
    onCompleted: (DecompResult) -> Unit
) : VBox(15.0) {

    private val progressBar = ProgressBar(0.0)
    private val stageLabel = Label("Preparing...")
    private val logArea = TextArea()
    private val cancelButton = Button("Cancel")
    private val timeLabel = Label("Remaining: --:--")

    private val scope = CoroutineScope(Dispatchers.JavaFx + SupervisorJob())
    private var startTime = System.currentTimeMillis()
    private var currentStage = ""
    private var totalClasses = 0
    private var completedClasses = 0

    init {
        padding = Insets(30.0)
        alignment = Pos.TOP_CENTER
        styleClass.add("progress-view")

        val title = Label("Decompiling $version")
        title.font = Font.font("System", FontWeight.BOLD, 24.0)
        title.styleClass.add("screen-title")

        stageLabel.font = Font.font("System", 14.0)
        stageLabel.styleClass.add("stage-label")

        progressBar.prefWidth = 600.0
        progressBar.styleClass.add("progress-bar")

        logArea.prefWidth = 600.0
        logArea.prefHeight = 300.0
        logArea.isEditable = false
        logArea.styleClass.add("log-area")

        cancelButton.styleClass.add("danger-button")
        cancelButton.setOnAction { onCancel() }

        val bottomBox = HBox(20.0, timeLabel, cancelButton)
        bottomBox.alignment = Pos.CENTER

        children.addAll(title, stageLabel, progressBar, logArea, bottomBox)

        scope.launch {
            progressBus.events.collect { event ->
                Platform.runLater {
                    when (event) {
                        is PipelineEvent.StageStarted -> {
                            currentStage = event.stage
                            stageLabel.text = getStageName(event.stage)
                            log("[INFO] Stage started: ${getStageName(event.stage)}")
                        }
                        is PipelineEvent.StageProgress -> {
                            completedClasses = event.current
                            totalClasses = event.total
                            val progress = if (event.total > 0) event.current.toDouble() / event.total else 0.0
                            progressBar.progress = progress
                            updateTimeEstimate()
                        }
                        is PipelineEvent.StageCompleted -> {
                            log("[INFO] Stage completed: ${getStageName(event.stage)}")
                        }
                        is PipelineEvent.Log -> {
                            val prefix = when (event.level) {
                                LogLevel.INFO -> "[INFO]"
                                LogLevel.WARN -> "[WARN]"
                                LogLevel.ERROR -> "[ERROR]"
                            }
                            log("$prefix ${event.message}")
                        }
                        is PipelineEvent.Completed -> {
                            cancelButton.isDisable = true
                            scope.cancel()
                            onCompleted(event.result)
                        }
                        is PipelineEvent.Failed -> {
                            log("[ERROR] Failed: ${event.error}")
                            // Leave a way out: the failure is terminal, so the
                            // cancel button becomes a back button.
                            cancelButton.text = "Back"
                            cancelButton.isDisable = false
                            scope.cancel()
                        }
                    }
                }
            }
        }
    }

    private fun getStageName(stage: String): String = when (stage) {
        "download" -> "Downloading files"
        "mappings" -> "Loading mappings"
        "remap" -> "Deobfuscating"
        "decompile" -> "Decompiling"
        "layout" -> "Laying out files"
        "done" -> "Done"
        else -> stage
    }

    private fun log(message: String) {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        logArea.appendText("$time $message\n")
        logArea.scrollTop = Double.MAX_VALUE
    }

    private fun updateTimeEstimate() {
        if (totalClasses > 0 && completedClasses > 0) {
            val elapsed = System.currentTimeMillis() - startTime
            val rate = completedClasses.toDouble() / elapsed
            val remaining = ((totalClasses - completedClasses) / rate / 1000).toLong()
            val minutes = remaining / 60
            val seconds = remaining % 60
            timeLabel.text = "Remaining: %02d:%02d".format(minutes, seconds)
        }
    }
}
