package dev.sz.aid

import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private fun Long.elapsedSec(): Long = (System.nanoTime() - this) / 1_000_000_000

class ProgressLogger(
    private val enabled: Boolean = false,
    private val out: PrintStream = System.err,
    private val waitIntervalSec: Long = 10,
) : AutoCloseable {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val startNanos: Long = System.nanoTime()
    private var waitTask: ScheduledFuture<*>? = null
    private val executor: ScheduledExecutorService? = if (enabled) {
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "progress-logger").apply { isDaemon = true }
        }
    } else null

    init {
        require(waitIntervalSec > 0) { "waitIntervalSec must be positive" }
    }

    fun log(message: String) {
        if (!enabled) return
        out.println("[${LocalDateTime.now().format(formatter)}] $message")
    }

    /**
     * Logs [message] and (re)starts the periodic "Waiting..." indicator.
     */
    fun progress(message: String) {
        if (!enabled) return
        log(message)
        scheduleWaitLog()
    }

    private fun scheduleWaitLog() {
        val executor = executor ?: return
        waitTask?.cancel(false)
        val waitStartNanos: Long = System.nanoTime()
        waitTask = executor.scheduleAtFixedRate({
            log("Waiting... ${waitStartNanos.elapsedSec()}s")
        }, waitIntervalSec, waitIntervalSec, TimeUnit.SECONDS)
    }

    override fun close() {
        log("Execution time: ${startNanos.elapsedSec()}s")
        executor?.shutdownNow()
    }
}
