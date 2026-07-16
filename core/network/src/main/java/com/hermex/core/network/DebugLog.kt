package com.hermex.core.network

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Thread-safe in-memory ring buffer for debug logging.
 * All networking calls and SSE events write here automatically.
 * Call [export] to dump the full buffer as a string for sharing.
 */
object DebugLog {

    private const val MAX_ENTRIES = 1000
    private val buffer = ConcurrentLinkedDeque<Entry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // Timestamp of first and last entry for export header
    private var firstTs: Long = System.currentTimeMillis()
    @Volatile private var lastTs: Long = System.currentTimeMillis()

    data class Entry(
        val timestamp: Long,
        val level: String,    // "REQ", "RESP", "SSE", "ERROR", "INFO"
        val tag: String,
        val message: String,
    )

    /** Log a message. Thread-safe. */
    fun log(level: String, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        synchronized(buffer) {
            if (buffer.isEmpty()) firstTs = entry.timestamp
            while (buffer.size >= MAX_ENTRIES) buffer.pollFirst()
            buffer.addLast(entry)
        }
        lastTs = entry.timestamp
    }

    /** Log with a throwable — captures full stack trace. */
    fun log(level: String, tag: String, message: String, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        log(level, tag, "$message\n${sw}")
    }

    /** Shortcut for request logging. */
    fun req(method: String, url: String, headers: String) {
        log("REQ", "HTTP", "$method $url\n$headers")
    }

    /** Shortcut for response logging. */
    fun resp(code: Int, url: String, body: String?) {
        val truncated = if (body != null && body.length > 2000) body.take(2000) + "\n... [truncated ${body.length} total]" else body
        log("RESP", "HTTP", "$code $url${if (truncated != null) "\n$truncated" else ""}")
    }

    /** Shortcut for SSE event logging. */
    fun sse(tag: String, message: String) {
        log("SSE", tag, message)
    }

    /** Shortcut for error logging. */
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) log("ERROR", tag, message, throwable)
        else log("ERROR", tag, message)
    }

    /** Export the entire buffer as a single string. */
    fun export(appVersion: String, deviceInfo: String, serverUrl: String): String {
        val sb = StringBuilder()
        sb.appendLine("=== Hermex Debug Log ===")
        sb.appendLine("App version: $appVersion")
        sb.appendLine("Device: $deviceInfo")
        sb.appendLine("Server: $serverUrl")
        sb.appendLine("Period: ${dateFormat.format(Date(firstTs))} — ${dateFormat.format(Date(lastTs))}")
        sb.appendLine("Entries: ${buffer.size} / $MAX_ENTRIES")
        sb.appendLine("=" .repeat(60))
        sb.appendLine()

        synchronized(buffer) {
            for (entry in buffer) {
                sb.appendLine("[${dateFormat.format(Date(entry.timestamp))}] [${entry.level}] ${entry.tag}")
                if (entry.message.contains('\n')) {
                    // Indent multi-line messages
                    entry.message.lines().forEach { line ->
                        sb.appendLine("  $line")
                    }
                } else {
                    sb.appendLine("  ${entry.message}")
                }
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    /** Return buffer contents as a short string (for clipboard). */
    fun exportShort(appVersion: String, serverUrl: String): String {
        val sb = StringBuilder()
        sb.appendLine("Hermex v$appVersion | $serverUrl")
        sb.appendLine("${buffer.size} entries")
        sb.appendLine("=" .repeat(40))
        synchronized(buffer) {
            for (entry in buffer) {
                sb.appendLine("[${entry.level}] ${entry.tag}: ${entry.message.take(200)}")
            }
        }
        return sb.toString()
    }

    /** Clear the buffer (for testing). */
    fun clear() {
        synchronized(buffer) { buffer.clear() }
        firstTs = System.currentTimeMillis()
    }

    /** Number of entries in the buffer. */
    fun entryCount(): Int = buffer.size
}
