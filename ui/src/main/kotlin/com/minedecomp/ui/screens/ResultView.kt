package com.minedecomp.ui.screens

import com.minedecomp.core.DecompResult
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import java.awt.Desktop
import java.io.File

class ResultView(
    result: DecompResult,
    onBackToMain: () -> Unit
) : VBox(15.0) {

    init {
        padding = Insets(30.0)
        alignment = Pos.TOP_CENTER
        styleClass.add("result-view")

        val title = Label("Decompilation Result")
        title.font = Font.font("System", FontWeight.BOLD, 24.0)
        title.styleClass.add("screen-title")

        val statusLabel = if (result.success) {
            Label("\u2713 Decompilation completed successfully").apply {
                textFill = Color.web("#4CAF50")
                font = Font.font("System", FontWeight.BOLD, 18.0)
            }
        } else {
            Label("\u2717 Decompilation completed with errors").apply {
                textFill = Color.web("#F44336")
                font = Font.font("System", FontWeight.BOLD, 18.0)
            }
        }

        val summary = Label("Decompiled classes: ${result.classesDecompiled}")
        summary.font = Font.font("System", 14.0)

        val errorSummary = if (result.classesWithErrors.isNotEmpty()) {
            Label("Classes with errors: ${result.classesWithErrors.size}").apply {
                textFill = Color.web("#FF9800")
                font = Font.font("System", 14.0)
            }
        } else {
            Label("Decompilation errors: none").apply {
                textFill = Color.web("#4CAF50")
                font = Font.font("System", 14.0)
            }
        }

        val outputLabel = Label("Output folder: ${result.outputDir}")
        outputLabel.font = Font.font("System", 12.0)
        outputLabel.styleClass.add("path-label")

        val openFolderButton = Button("Open result folder")
        openFolderButton.styleClass.add("primary-button")
        openFolderButton.setOnAction {
            try {
                Desktop.getDesktop().open(File(result.outputDir))
            } catch (e: Exception) {
                println("Cannot open folder: ${e.message}")
            }
        }

        val backButton = Button("\u2190 Back to main menu")
        backButton.styleClass.add("secondary-button")
        backButton.setOnAction { onBackToMain() }

        val errorList = if (result.classesWithErrors.isNotEmpty()) {
            VBox(5.0).apply {
                val errorTitle = Label("Files with decompilation errors:")
                errorTitle.font = Font.font("System", FontWeight.BOLD, 14.0)
                errorTitle.textFill = Color.web("#FF9800")

                val listView = ListView<String>().apply {
                    items.addAll(result.classesWithErrors)
                    prefHeight = 200.0
                    prefWidth = 600.0
                }

                children.addAll(errorTitle, listView)
            }
        } else null

        val buttonBox = HBox(15.0, openFolderButton, backButton)
        buttonBox.alignment = Pos.CENTER

        val content = VBox(10.0, statusLabel, summary, errorSummary, outputLabel)
        content.alignment = Pos.CENTER

        children.addAll(title, content)
        if (errorList != null) {
            children.addAll(errorList)
        }
        children.add(buttonBox)
    }
}
