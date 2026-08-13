package dev.sz.aid

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name

class AidCommandTest {

    @Test
    fun `parses required arguments correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3"
        )
        cmd.projectDir shouldBe Paths.get("/test/repo")
        cmd.scope shouldBe CodeScope.DIFF
        cmd.commit shouldBe "HEAD"
        cmd.sources shouldBe emptyList()
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
    fun `parses sources option correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/some/path",
            "-m", "model",
            "--sources", "/path/to/source1",
            "--sources", "/path/to/source2"
        )
        cmd.sources shouldBe listOf("/path/to/source1", "/path/to/source2")
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
        }.message.shouldContain("Invalid value for option '--scope'")
    }

    @Test
    fun `requires sources when scope is SOURCES`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")

        assertThrows<IllegalArgumentException> {
            CommandLine.populateCommand(
                AidCommand(),
                "--dir", gitDir.absolutePathString(),
                "--model", "llama123",
                "--scope", "sources",
            ).run()
        }.message.shouldContain("At least one --sources option must be specified")
    }

    @Test
    fun `rejects empty sources`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")

        assertThrows<IllegalArgumentException> {
            CommandLine.populateCommand(
                AidCommand(),
                "-d", gitDir.absolutePathString(),
                "-m", "llama123",
                "-s", "sources",
                "-S", "",
            ).run()
        }.message.shouldContain("Blank path")
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
            capturedStr.shouldStartWith("review")
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
            capturedStr.shouldStartWith("custom")
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
                    string(Charsets.UTF_8).shouldContain("\"enable_thinking\"")
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
                headers.shouldContain("Authorization" to "Bearer key")
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
                headers.shouldContain("Authorization" to "Bearer abcdefghijklmnop")
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
                headers.shouldContain("Authorization" to "Bearer key2")
            }
        }
    }

    @Test
    fun `full run with sources scope`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        val dir1 = gitDir.resolve("dir1")
        Files.createDirectories(dir1)
        val file1 = dir1.resolve("8920571290.txt")
        Files.write(file1, "5891257128903\n".toByteArray())
        val dir2 = gitDir.resolve("dir2")
        Files.createDirectories(dir2)
        val file2 = dir2.resolve("1357901235.txt")
        Files.write(file2, "58971236579823\n".toByteArray())
        val file3 = gitDir.resolve("519283750129.txt")
        Files.write(file3, "58971236579823\n".toByteArray())
        val file4 = gitDir.resolve("5982731589023175.txt")
        Files.write(file4, "58971236579823\n".toByteArray())
        gitDir.runProcess("git", "add", ".")

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
                    "--scope", "sources",
                    "--sources", dir1.name,
                    "-S", file3.absolutePathString(),
                    "--url", llmServer.url("/").toString(),
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    val strBody = string(Charsets.UTF_8)
                    strBody.shouldContain(file1.name)
                    strBody.shouldNotContain(file2.name)
                    strBody.shouldContain(file3.name)
                    strBody.shouldNotContain(file4.name)
                }
            }
        }
    }
}