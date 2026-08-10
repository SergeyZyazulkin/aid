package dev.sz.aid

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory

class AidCommandTest {

    @Test
    fun `parses required arguments correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3"
        )
        cmd.projectDir shouldBe "/test/repo"
        cmd.scope shouldBe CodeScope.DIFF
        cmd.commit shouldBe "HEAD"
        cmd.url shouldBe "http://127.0.0.1:11434"
        cmd.apiKey shouldBe null
        cmd.model shouldBe "llama3"
        cmd.connectTimeoutSec shouldBe 10L
        cmd.readTimeoutSec shouldBe 300L
        cmd.promptPath shouldBe null
        cmd.forceThinking shouldBe false
        cmd.codeLimit shouldBe 256000
        cmd.debugCodeContent shouldBe false
    }

    @Test
    fun `allows custom scope and prompt, forced thinking, debug code content`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "--dir", "/test/repo",
            "--model", "test",
            "--scope", "all",
            "--prompt", "/custom/prompt.md",
            "--force-thinking",
            "--debug-code-content",
        )
        cmd.scope shouldBe CodeScope.ALL
        cmd.promptPath shouldBe "/custom/prompt.md"
        cmd.forceThinking shouldBe true
        cmd.debugCodeContent shouldBe true
    }

    @Test
    fun `rejects invalid scope`() {
        assertThrows<CommandLine.ParameterException> {
            CommandLine.populateCommand(
                AidCommand(),
                "--dir", "/test/repo",
                "--model", "test",
                "--scope", "invalid",
            )
        }.message?.contains("Invalid value for option '--scope'") shouldBe true
    }

    @Test
    fun `full run with review prompt`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("dummy.txt"), "line1\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")
        Files.write(gitDir.resolve("dummy2.txt"), "line2\n".toByteArray())
        gitDir.runProcess("git", "add", ".")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"review\"}}]}")
                    .build()
            )

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand())
                    .execute(
                        "-d", gitDir.absolutePathString(),
                        "-m", "test",
                        "-u", llmServer.url("/").toString(),
                    )
            } finally {
                System.setOut(originalOut)
            }
            val capturedStr = String(captured.toByteArray(), Charsets.UTF_8)
            capturedStr.startsWith("review") shouldBe true
        }
    }

    @Test
    fun `full run with custom prompt`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("README.md"), "README content\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")
        val promptFile = gitDir.resolve("prompt.md")
        Files.write(promptFile, "custom prompt\n".toByteArray())
        gitDir.runProcess("git", "add", ".")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"custom\"}}]}")
                    .build()
            )

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand())
                    .execute(
                        "--dir", gitDir.absolutePathString(),
                        "--model", "test",
                        "--scope", "all",
                        "--url", llmServer.url("/").toString(),
                        "--prompt", promptFile.absolutePathString(),
                    )
            } finally {
                System.setOut(originalOut)
            }
            val capturedStr = String(captured.toByteArray(), Charsets.UTF_8)
            capturedStr.startsWith("custom") shouldBe true
        }
    }

    @Test
    fun `full run with forced thinking`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "some text\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "first")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"msg\"}}]}")
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "--dir", gitDir.absolutePathString(),
                    "--model", "test",
                    "--scope", "all",
                    "--url", llmServer.url("/").toString(),
                    "-t",
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8).contains("\"enable_thinking\"") shouldBe true
                }
            }
        }
    }

    @Test
    fun `full run with API key from argument`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "hgjdak\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "first")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"msg\"}}]}")
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "-k", "key",
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                headers.contains("Authorization" to "Bearer key") shouldBe true
            }
        }
    }

    @Test
    fun `full run with API key from environment`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("gdkasd.txt"), "hgjdak\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "asdgn")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"msg\"}}]}")
                    .build()
            )

            val testEnvironment = Environment { name ->
                if (name == "AID_API_KEY") "abcdefghijklmnop" else null
            }

            CommandLine(AidCommand(environment = testEnvironment))
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                headers.contains("Authorization" to "Bearer abcdefghijklmnop") shouldBe true
            }
        }
    }

    @Test
    fun `full run with API key from both sources`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("123.txt"), "123\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "123")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"msg\"}}]}")
                    .build()
            )

            val testEnvironment = Environment { name ->
                if (name == "AID_API_KEY") "key1" else null
            }

            CommandLine(AidCommand(environment = testEnvironment))
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--api-key", "key2"
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                headers.contains("Authorization" to "Bearer key2") shouldBe true
            }
        }
    }
}