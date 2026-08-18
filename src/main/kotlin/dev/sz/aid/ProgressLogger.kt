package dev.sz.aid

import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private fun Long.elapsedSec(): Long = (System.nanoTime() - this) / 1_000_000_000

class ProgressLogger(
    private val enabled: Boolean = false,
    private val out: PrintStream = System.err,
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val startNanos: Long = System.nanoTime()

    fun log(message: String) {
        if (enabled) out.println("[${LocalDateTime.now().format(formatter)}] $message")
    }

    /**
     * Starts a background daemon that prints elapsed seconds every [intervalSec] seconds.
     * Returns an [AutoCloseable] that shuts the daemon down.
     */
    fun startWaitLogging(intervalSec: Long = 5): AutoCloseable {
        require(intervalSec > 0) { "intervalSec must be positive" }
        if (!enabled) return {}
        val waitStartNanos: Long = System.nanoTime()

        val executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "progress-logger").apply { isDaemon = true }
        }

        executor.scheduleAtFixedRate({
            log("Waiting... ${waitStartNanos.elapsedSec()}s")
        }, intervalSec, intervalSec, TimeUnit.SECONDS)

        return { executor.shutdownNow() }
    }

    fun logCompletion() = log("Completed in ${startNanos.elapsedSec()}s")
}
