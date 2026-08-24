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
    fun `rejects negative waiting interval`() {
        shouldThrow<IllegalArgumentException> {
            ProgressLogger(enabled = true, waitIntervalSec = 0)
        }.message.shouldContain("waitIntervalSec must be positive")
    }

    @Test
    fun `logs message and execution time with timestamp when enabled`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)

        ProgressLogger(enabled = true, out = out).use {
            it.log("Message...")
        }

        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldMatch(
                Regex(
                    "^\\[\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}] Message\\.\\.\\.\\s+" +
                            "\\[\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}] Execution time: [0-9]+s\\s*$",
                    RegexOption.MULTILINE
                )
            )
    }

    @Test
    fun `does not log when disabled`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)

        ProgressLogger(enabled = false, out = out).use {
            it.log("should not appear")
        }

        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldNotContain("should not appear")
            .shouldNotContain("Execution time")
    }

    @Test
    fun `prints periodic wait lines`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)

        ProgressLogger(enabled = true, out = out, waitIntervalSec = 1).use {
            it.progress("Progress...")
            Thread.sleep(2_000) // allow at least one tick
        }

        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldContain("Progress...")
            .shouldContain("Waiting...")
    }

    @Test
    fun `no wait lines when disabled`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)

        ProgressLogger(enabled = false, out = out, waitIntervalSec = 1).use {
            it.progress("Progress...")
            Thread.sleep(2_000) // allow at least one tick
        }

        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldNotContain("Progress...")
            .shouldNotContain("Waiting...")
    }

    @Test
    fun `no wait lines after close`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)

        ProgressLogger(enabled = true, out = out, waitIntervalSec = 1).use {
            it.progress("Progress...")
        }
        Thread.sleep(2_000) // past the interval, but executor should be shut down

        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldContain("Progress...")
            .shouldNotContain("Waiting...")
    }

    @Test
    fun `logs execution time even when body throws`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)

        shouldThrow<IllegalStateException> {
            ProgressLogger(enabled = true, out = out).use {
                it.log("Starting...")
                throw IllegalStateException("boom")
            }
        }

        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldContain("Starting...")
            .shouldContain("Execution time:")
    }

    @Test
    fun `progress call resets the wait timer`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)

        ProgressLogger(enabled = true, out = out, waitIntervalSec = 2).use {
            it.progress("First")
            Thread.sleep(1_000)
            it.progress("Second") // reset; 2s interval hasn't elapsed since the previous call
            Thread.sleep(1_000)
            it.progress("Third") // reset; 2s interval hasn't elapsed since the previous call
            Thread.sleep(1_000)
        } // close; 2s interval hasn't elapsed since the previous call

        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldContain("First")
            .shouldContain("Second")
            .shouldContain("Third")
            .shouldNotContain("Waiting...")
    }
}
