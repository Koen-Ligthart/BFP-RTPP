package main.util

import java.io.PrintWriter
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

// log file currently in use
private var currentLog: PrintWriter? = null

private val dir = Paths.get("logs")

/** Logs a message to the currently opened log with [newLog]. */
fun log(message: Any?) {
    currentLog?.println(message) ?: throw IllegalStateException("no log created yet")
    println("[LOG] $message")
}

/**
 * Opens a new log file or overwrites an existing log file with file name [name].
 * Replaces and closes the currently opened log file if it exists.
 */
fun newLog(name: String) {
    currentLog?.close()
    dir.createDirectories()
    currentLog = PrintWriter(dir.resolve("$name.log").outputStream(), true)
    currentLog!!.println("Log [${Date()}]")
}