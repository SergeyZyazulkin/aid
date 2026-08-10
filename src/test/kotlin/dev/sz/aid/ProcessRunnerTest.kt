package dev.sz.aid

import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class ProcessRunnerTest {

    private val tempDir: Path by lazy { createTempDirectory("aid-test-") }

    @AfterTest
    fun cleanup() = unmockkAll()

    @Test
    fun `runs command successfully and captures stdout`() {
        val randomOutput = Uuid.random().toString()

        mockkConstructor(ProcessBuilder::class)
        every { anyConstructed<ProcessBuilder>().start() } answers {
            val pb = mockk<Process>()
            every { pb.waitFor() } returns 0
            every { pb.inputStream } returns randomOutput.byteInputStream()
            every { pb.errorStream } returns "".byteInputStream()
            pb
        }

        tempDir.runProcess("test") shouldBe randomOutput
    }

    @Test
    fun `throws on non-zero exit code`() {
        mockkConstructor(ProcessBuilder::class)
        every { anyConstructed<ProcessBuilder>().start() } answers {
            val pb = mockk<Process>()
            every { pb.waitFor() } returns 1
            every { pb.inputStream } returns "".byteInputStream()
            every { pb.errorStream } returns "".byteInputStream()
            every { pb.destroyForcibly() } returns pb
            pb
        }

        assertThrows<IllegalStateException> {
            tempDir.runProcess("test")
        }.message?.contains("exit code 1") shouldBe true
    }

    @Test
    fun `times out and kills process`() {
        mockkConstructor(ProcessBuilder::class)
        every { anyConstructed<ProcessBuilder>().start() } answers {
            val pb = mockk<Process>()
            every { pb.waitFor() } just awaits
            every { pb.inputStream } returns "".byteInputStream()
            every { pb.errorStream } returns "".byteInputStream()
            every { pb.destroyForcibly() } returns pb
            pb
        }

        assertThrows<IllegalStateException> {
            tempDir.runProcess("test", timeout = 100.milliseconds)
        }.message?.contains("timed out") shouldBe true
    }

    @Test
    fun `logs stderr if non-empty but exit code is 0`() {
        val randomOutput = Uuid.random().toString()

        mockkConstructor(ProcessBuilder::class)
        every { anyConstructed<ProcessBuilder>().start() } answers {
            val pb = mockk<Process>()
            every { pb.waitFor() } returns 0
            every { pb.inputStream } returns "".byteInputStream()
            every { pb.errorStream } returns randomOutput.byteInputStream()
            pb
        }

        val originalErr = System.err
        val captured = ByteArrayOutputStream()
        try {
            System.setErr(PrintStream(captured, true, Charsets.UTF_8))
            tempDir.runProcess("test")
        } finally {
            System.setErr(originalErr)
        }
        val capturedStr = String(captured.toByteArray(), Charsets.UTF_8)
        capturedStr.startsWith(randomOutput) shouldBe true
    }
}