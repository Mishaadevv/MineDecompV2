package com.minedecomp.core

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FileLogger(private val logFile: File) {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    init {
        logFile.parentFile?.mkdirs()
    }

    fun log(level: LogLevel, message: String) {
        val timestamp = LocalDateTime.now().format(formatter)
        val line = "[$timestamp] [${level.name}] $message"
        logFile.appendText("$line\n")
    }

    fun info(message: String) = log(LogLevel.INFO, message)
    fun warn(message: String) = log(LogLevel.WARN, message)
    fun error(message: String) = log(LogLevel.ERROR, message)
}
