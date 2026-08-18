package dev.sz.aid

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ProgressLoggerTest {

    @Test
    fun `logs message with timestamp when enabled`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)

        ProgressLogger(enabled = true, out = out)
            .log("Message...")

        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldMatch(Regex("^\\[\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}] Message\\.\\.\\.\\s*"))
    }

    @Test
    fun `does not log when disabled`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)

        ProgressLogger(enabled = false, out = out)
            .log("should not appear")

        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldNotContain("should not appear")
    }

    @Test
    fun `startWaitLogging prints periodic wait lines`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)

        ProgressLogger(enabled = true, out = out)
            .startWaitLogging(intervalSec = 1)
            .use { Thread.sleep(2_000) } // allow at least one tick

        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldContain("Waiting...")
    }

    @Test
    fun `startWaitLogging is no-op when disabled`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)

        ProgressLogger(enabled = false, out = out)
            .startWaitLogging(intervalSec = 1)
            .use { Thread.sleep(2_000) } // allow at least one tick

        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldNotContain("Waiting...")
    }

    @Test
    fun `startWaitLogging rejects negative interval`() {
        shouldThrow<IllegalArgumentException> {
            ProgressLogger(enabled = true)
                .startWaitLogging(intervalSec = 0)
        }.message.shouldContain("intervalSec must be positive")
    }

    @Test
    fun `logCompletion prints total elapsed time`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)

        ProgressLogger(enabled = true, out = out)
            .logCompletion()

        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldContain("Completed in ")
    }
}
