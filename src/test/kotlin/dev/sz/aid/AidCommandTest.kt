package dev.sz.aid

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
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
import kotlin.io.path.createTempFile
import kotlin.io.path.name

class AidCommandTest {

    @Test
    fun `parses required and default arguments correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3"
        )
        cmd.projectDir shouldBe Paths.get("/test/repo")
        cmd.scope shouldBe CodeScope.DIFF
        cmd.commit shouldBe "HEAD"
        cmd.sources shouldBe emptyList()
        cmd.fileFilters shouldBe emptyList()
        cmd.url shouldBe "http://127.0.0.1:11434"
        cmd.apiKey shouldBe null
        cmd.model shouldBe "llama3"
        cmd.connectTimeoutSec shouldBe 10L
        cmd.readTimeoutSec shouldBe 300L
        cmd.promptPath shouldBe null
        cmd.forceThinking shouldBe false
        cmd.codeLimit shouldBe 256000
        cmd.debugCodeContent shouldBe false
        cmd.lang shouldBe OutputLanguage.EN
        cmd.stream shouldBe false
        cmd.usage shouldBe false
        cmd.contextLines shouldBe null
        cmd.includeReasoning shouldBe false
        cmd.promptVersion shouldBe Prompts.Version.V1
        cmd.printArgs shouldBe false
        cmd.contextFiles shouldBe emptyList()
        cmd.interactive shouldBe false
        cmd.maxTurns shouldBe 20
        cmd.temperature shouldBe null
        cmd.maxTokens shouldBe null
        cmd.topP shouldBe null
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
    fun `parses ru lang`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "--dir", "/test/repo",
            "--model", "test",
            "--lang", "ru",
        )
        cmd.lang shouldBe OutputLanguage.RU
    }

    @Test
    fun `parses filter option correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "model",
            "-f", "*.java",
            "-f", "**.kt",
        )
        cmd.fileFilters shouldBe listOf("*.java", "**.kt")
    }

    @Test
    fun `parses stream option correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3",
            "--stream",
        )
        cmd.stream shouldBe true
    }

    @Test
    fun `parses usage flag correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3",
            "--usage",
        )
        cmd.usage shouldBe true
    }

    @Test
    fun `parses context-lines option correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "model",
            "-U", "10",
        )
        cmd.contextLines shouldBe 10
    }

    @Test
    fun `parses context-lines long form correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "model",
            "--context-lines", "0",
        )
        cmd.contextLines shouldBe 0
    }

    @Test
    fun `parses --reasoning flag correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3",
            "--reasoning",
        )
        cmd.includeReasoning shouldBe true
    }

    @Test
    fun `parses prompt-version option correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3",
            "--prompt-version", "v2",
        )
        cmd.promptVersion shouldBe Prompts.Version.V2
    }

    @Test
    fun `parses prompt-version none correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3",
            "--prompt-version", "none",
        )
        cmd.promptVersion shouldBe Prompts.Version.NONE
    }

    @Test
    fun `parses --print-args flag correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3",
            "--print-args",
        )
        cmd.printArgs shouldBe true
    }

    @Test
    fun `parses --context option correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3",
            "--context", "/path/to/issue.md",
            "--context", "/path/to/trace.txt",
        )
        cmd.contextFiles shouldBe listOf(Paths.get("/path/to/issue.md"), Paths.get("/path/to/trace.txt"))
    }

    @Test
    fun `parses --interactive flag correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3",
            "--interactive",
        )
        cmd.interactive shouldBe true
    }

    @Test
    fun `parses -i short flag correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3",
            "-i",
        )
        cmd.interactive shouldBe true
    }

    @Test
    fun `parses --max-turns correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3",
            "--interactive",
            "--max-turns", "5",
        )
        cmd.maxTurns shouldBe 5
    }

    @Test
    fun `parses sampling options correctly`() {
        val cmd = CommandLine.populateCommand(
            AidCommand(),
            "-d", "/test/repo",
            "-m", "llama3",
            "--temperature", "0.1",
            "--max-tokens", "4096",
            "--top-p", "0.9",
        )
        cmd.temperature shouldBe 0.1f
        cmd.maxTokens shouldBe 4096
        cmd.topP shouldBe 0.9f
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
    fun `rejects invalid language`() {
        assertThrows<CommandLine.ParameterException> {
            CommandLine.populateCommand(
                AidCommand(),
                "--dir", "/test/repo",
                "--model", "test",
                "--lang", "invalid",
            )
        }.message.shouldContain("Invalid value for option '--lang'")
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

        assertThrows<IllegalStateException> {
            CommandLine.populateCommand(
                AidCommand(),
                "-d", gitDir.absolutePathString(),
                "-m", "llama123",
                "-s", "sources",
                "-S", "",
            ).run()
        }.message.shouldContain("empty string is not a valid pathspec")
    }

    @Test
    fun `rejects invalid prompt-version`() {
        assertThrows<CommandLine.ParameterException> {
            CommandLine.populateCommand(
                AidCommand(),
                "-d", "/test/repo",
                "-m", "test",
                "--prompt-version", "v99",
            )
        }.message.shouldContain("--prompt-version")
    }

    @Test
    fun `rejects invalid --max-turns`() {
        assertThrows<IllegalArgumentException> {
            CommandLine.populateCommand(
                AidCommand(),
                "--dir", "/test/repo",
                "--model", "test",
                "--interactive",
                "--max-turns", "1",
            ).run()
        }.message.shouldContain("--max-turns must be at least 2")
    }

    @Test
    fun `rejects out-of-range temperature`() {
        assertThrows<IllegalArgumentException> {
            CommandLine.populateCommand(
                AidCommand(),
                "-d", "/test/repo",
                "-m", "test",
                "--temperature", "3.0",
            ).run()
        }.message.shouldContain("temperature must be in [0, 2]")
    }

    @Test
    fun `rejects negative max-tokens`() {
        assertThrows<IllegalArgumentException> {
            CommandLine.populateCommand(
                AidCommand(),
                "-d", "/test/repo",
                "-m", "test",
                "--max-tokens", "-1",
            ).run()
        }.message.shouldContain("max_tokens must be positive")
    }

    @Test
    fun `rejects out-of-range top-p`() {
        assertThrows<IllegalArgumentException> {
            CommandLine.populateCommand(
                AidCommand(),
                "-d", "/test/repo",
                "-m", "test",
                "--top-p", "1.5",
            ).run()
        }.message.shouldContain("top_p must be in [0, 1]")
    }

    @Test
    fun `fails with explicit error when diff produces no changes`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "content\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        // No uncommitted changes — git diff HEAD is empty
        assertThrows<IllegalArgumentException> {
            CommandLine.populateCommand(
                AidCommand(),
                "-d", gitDir.absolutePathString(),
                "-m", "test",
                "-s", "diff",
            ).run()
        }.message.shouldContain("No code collected (result is blank)")
    }

    @Test
    fun `fails with explicit error when filter matches no files`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("App.java"), "public class App {}".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        // Filter matches only .xml files, but repo has only .java — blank result
        assertThrows<IllegalArgumentException> {
            CommandLine.populateCommand(
                AidCommand(),
                "-d", gitDir.absolutePathString(),
                "-m", "test",
                "-s", "all",
                "-f", "**.xml",
            ).run()
        }.message.shouldContain("No code collected (result is blank)")
    }

    @Test
    fun `--context with missing file fails with clear error`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        assertThrows<IllegalArgumentException> {
            CommandLine.populateCommand(
                AidCommand(),
                "-d", gitDir.absolutePathString(),
                "-m", "test",
                "-s", "all",
                "--context", "/nonexistent/context.md",
            ).run()
        }.message.shouldContain("Context file does not exist:")
    }

    @Test
    fun `--context with directory fails with clear error`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val dir = createTempDirectory("aid-ctx-dir-")

        assertThrows<IllegalArgumentException> {
            CommandLine.populateCommand(
                AidCommand(),
                "-d", gitDir.absolutePathString(),
                "-m", "test",
                "-s", "all",
                "--context", dir.absolutePathString(),
            ).run()
        }.message.shouldContain("Not a regular file")
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
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"review"}}]}""")
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
            String(captured.toByteArray(), Charsets.UTF_8)
                .shouldStartWith("review")
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
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"custom"}}]}""")
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
            String(captured.toByteArray(), Charsets.UTF_8)
                .shouldStartWith("custom")
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
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"msg"}}]}""")
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
                    string(Charsets.UTF_8)
                        .shouldContain("\"enable_thinking\"")
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
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"msg"}}]}""")
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
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"msg"}}]}""")
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
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"msg"}}]}""")
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
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"msg"}}]}""")
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
                    string(Charsets.UTF_8)
                        .shouldContain(file1.name)
                        .shouldNotContain(file2.name)
                        .shouldContain(file3.name)
                        .shouldNotContain(file4.name)
                }
            }
        }
    }

    @Test
    fun `--dry-run prints request JSON and skips LLM call`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("source.txt"), "some code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        // start the server to check that it wasn't called
        MockWebServer().use { llmServer ->
            llmServer.start()

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand())
                    .execute(
                        "-d", gitDir.absolutePathString(),
                        "-m", "test",
                        "-s", "all",
                        "-u", llmServer.url("/").toString(),
                        "--dry-run",
                    )
            } finally {
                System.setOut(originalOut)
            }
            String(captured.toByteArray(), Charsets.UTF_8)
                .shouldStartWith("{")
                .shouldContain("\"model\"")
                .shouldContain("\"messages\"")
                .shouldContain("\"role\"")
                .shouldContain("\"system\"")
                .shouldContain("\"user\"")
                .shouldContain("\"content\"")
                .shouldContain("some code")

            llmServer.requestCount shouldBe 0
        }
    }

    @Test
    fun `--lang ru appends Russian directive to system prompt`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"отлично"}}]}""")
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--lang", "ru",
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("## Language")
                        .shouldContain("Respond entirely in Russian")
                }
            }
        }
    }

    @Test
    fun `--lang en does not append language directive`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "--dir", gitDir.absolutePathString(),
                    "--model", "test",
                    "--scope", "all",
                    "--url", llmServer.url("/").toString(),
                    "--lang", "en",
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldNotContain("## Language")
                        .shouldNotContain("Respond entirely in Russian")
                }
            }
        }
    }

    @Test
    fun `--lang ru with custom prompt still appends directive`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val customPrompt = createTempFile("custom-prompt", ".md")
        Files.write(customPrompt, "Проанализируй код на предмет наличия багов.\n".toByteArray())

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"багов нет"}}]}""")
                    .build()
            )

            CommandLine(AidCommand()).execute(
                "-d", gitDir.absolutePathString(),
                "-m", "test",
                "-s", "all",
                "-u", llmServer.url("/").toString(),
                "--prompt", customPrompt.absolutePathString(),
                "--lang", "ru",
            )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("Проанализируй код на предмет наличия багов.")
                        .shouldContain("## Language")
                        .shouldContain("Respond entirely in Russian")
                }
            }
        }
    }

    @Test
    fun `--progress logs steps to stderr with timestamps`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"LLM output"}}]}""")
                    .build()
            )

            val originalOut = System.out
            val outBuf = ByteArrayOutputStream()
            val originalErr = System.err
            val errBuf = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
                System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--progress",
                )
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
            }

            val stderr = String(errBuf.toByteArray(), Charsets.UTF_8)
            stderr.shouldContain("Collecting code...")
                .shouldContain("Building prompt...")
                .shouldContain("Sending request to LLM...")
                .shouldContain("Printing result...")
                .shouldContain("Execution time: ")
            // Each line must start with a timestamp
            stderr.lines()
                .filter { it.isNotBlank() }
                .forEach { line ->
                    line.shouldMatch(Regex("^\\[\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}] .+"))
                }

            // stdout should still contain only the LLM result
            String(outBuf.toByteArray(), Charsets.UTF_8)
                .shouldStartWith("LLM output")
        }
    }

    @Test
    fun `full run with scope 'all' and filter includes only matching files`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        val srcDir = gitDir.resolve("src")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("App.java"), "public class App {}".toByteArray())
        Files.write(srcDir.resolve("Main.kt"), "fun main() {}".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-f", "**.java",
                    "-u", llmServer.url("/").toString(),
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("App.java")
                        .shouldContain("public class App {}")
                        .shouldNotContain("Main.kt")
                        .shouldNotContain("fun main() {}")
                }
            }
        }
    }

    @Test
    fun `full run with scope 'sources' and filter includes only matching files`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        val srcDir = gitDir.resolve("src")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("App.java"), "public class App {}".toByteArray())
        Files.write(srcDir.resolve("Main.kt"), "fun main() {}".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "sources",
                    "-S", "src",
                    "-f", "src/*.kt",
                    "-u", llmServer.url("/").toString(),
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("Main.kt")
                        .shouldContain("fun main() {}")
                        .shouldNotContain("App.java")
                        .shouldNotContain("public class App {}")
                }
            }
        }
    }

    @Test
    fun `full run with diff scope and sources`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        val srcDir = gitDir.resolve("src")
        Files.createDirectories(srcDir)
        val appJava = srcDir.resolve("App.java")
        Files.write(appJava, "public class App {}\n".toByteArray())
        val notesTxt = gitDir.resolve("notes.txt")
        Files.write(notesTxt, "some notes\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")
        // introduce changes
        Files.write(srcDir.resolve(appJava), "public class App { int x; }\n".toByteArray())
        Files.write(gitDir.resolve(notesTxt), "some notes 2\n".toByteArray())

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "diff",
                    "-S", ":!**.txt",
                    "-u", llmServer.url("/").toString(),
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("App.java")
                        // pathspec excludes it
                        .shouldNotContain("notes.txt")
                }
            }
        }
    }

    @Test
    fun `full run in streaming mode`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val sseBody = buildString {
            appendLine("""data: {"id":"1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"!"},"finish_reason":"stop"}]}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .headers(
                        Headers.Builder()
                            .add("Content-Type", "text/event-stream")
                            .build()
                    )
                    .body(sseBody)
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
                        "-s", "all",
                        "-u", llmServer.url("/").toString(),
                        "--stream",
                    )
            } finally {
                System.setOut(originalOut)
            }

            String(captured.toByteArray(), Charsets.UTF_8)
                .shouldContain("Hello world!")

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("\"stream\":true")
                }
            }
        }
    }

    @Test
    fun `--usage prints token usage to stderr in non-streaming mode`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body(
                        """{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}],
                        "usage":{"prompt_tokens":100,"completion_tokens":50,"total_tokens":150}}"""
                    )
                    .build()
            )

            val originalOut = System.out
            val outBuf = ByteArrayOutputStream()
            val originalErr = System.err
            val errBuf = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
                System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--usage",
                )
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
            }

            String(errBuf.toByteArray(), Charsets.UTF_8)
                .shouldContain("[USAGE] prompt=100, completion=50, total=150")

            // stdout should still contain only the LLM result
            String(outBuf.toByteArray(), Charsets.UTF_8)
                .shouldMatch(Regex("ok\\s*"))
        }
    }

    @Test
    fun `--usage not set means no usage line on stderr`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body(
                        """{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}],
                        "usage":{"prompt_tokens":100,"completion_tokens":50,"total_tokens":150}}"""
                    )
                    .build()
            )

            val originalErr = System.err
            val errBuf = ByteArrayOutputStream()
            try {
                System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                )
            } finally {
                System.setErr(originalErr)
            }

            String(errBuf.toByteArray(), Charsets.UTF_8)
                .shouldNotContain("[USAGE]")
        }
    }

    @Test
    fun `--usage prints token usage to stderr in streaming mode`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val sseBody = buildString {
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"role":"assistant","content":"Hi"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"content":" there"},"finish_reason":"stop"}],"usage":{"prompt_tokens":200,"completion_tokens":10,"total_tokens":210,"prompt_tokens_details":{"cached_tokens": 10},"completion_tokens_details":{"reasoning_tokens":5}}}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .headers(Headers.Builder().add("Content-Type", "text/event-stream").build())
                    .body(sseBody)
                    .build()
            )

            val originalOut = System.out
            val outBuf = ByteArrayOutputStream()
            val originalErr = System.err
            val errBuf = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
                System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--stream",
                    "--usage",
                )
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
            }

            String(outBuf.toByteArray(), Charsets.UTF_8)
                .shouldContain("Hi there")

            String(errBuf.toByteArray(), Charsets.UTF_8)
                .shouldContain("[USAGE] prompt=200 (cached=10), completion=10 (reasoning=5), total=210")
        }
    }

    @Test
    fun `--usage prints token usage to stderr in streaming mode when no usage is received`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val sseBody = buildString {
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"role":"assistant","content":"Hi"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"content":" there"},"finish_reason":"stop"}]}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .headers(Headers.Builder().add("Content-Type", "text/event-stream").build())
                    .body(sseBody)
                    .build()
            )

            val originalOut = System.out
            val outBuf = ByteArrayOutputStream()
            val originalErr = System.err
            val errBuf = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
                System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--stream",
                    "--usage",
                )
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
            }

            String(outBuf.toByteArray(), Charsets.UTF_8)
                .shouldContain("Hi there")

            String(errBuf.toByteArray(), Charsets.UTF_8)
                .shouldContain("[USAGE] no usage data in response")
        }
    }

    @Test
    fun `includes stream_options when streaming and usage requested`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val sseBody = buildString {
            appendLine("""data: {"id":"1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"!"},"finish_reason":"stop"}],"usage":{"prompt_tokens":200,"completion_tokens":10,"total_tokens":210}}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .headers(
                        Headers.Builder()
                            .add("Content-Type", "text/event-stream")
                            .build()
                    )
                    .body(sseBody)
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--stream",
                    "--usage",
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("\"stream_options\"")
                        .shouldContain("\"include_usage\"")
                }
            }
        }
    }

    @Test
    fun `doesn't include stream_options when usage isn't requested`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val sseBody = buildString {
            appendLine("""data: {"id":"1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"!"},"finish_reason":"stop"}]}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .headers(
                        Headers.Builder()
                            .add("Content-Type", "text/event-stream")
                            .build()
                    )
                    .body(sseBody)
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--stream",
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldNotContain("\"stream_options\"")
                        .shouldNotContain("\"include_usage\"")
                }
            }
        }
    }

    @Test
    fun `non-streaming with --reasoning includes blockquote in output`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"The answer is 42.","reasoning_content":"Let me think... 6*7=42."}}]}""")
                    .build()
            )

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--reasoning",
                )
            } finally {
                System.setOut(originalOut)
            }

            val output = String(captured.toByteArray(), Charsets.UTF_8)
            output.shouldContain("> **Reasoning**")
                .shouldContain("> Let me think... 6*7=42.")
                .shouldContain("The answer is 42.")
            // Reasoning must appear before content
            output.indexOf("Reasoning") shouldBeLessThan output.indexOf("The answer is 42.")
        }
    }

    @Test
    fun `non-streaming without --reasoning omits reasoning from output`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"The answer is 42.","reasoning_content":"Let me think..."}}]}""")
                    .build()
            )

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                )
            } finally {
                System.setOut(originalOut)
            }

            String(captured.toByteArray(), Charsets.UTF_8)
                .shouldNotContain("Reasoning")
                .shouldNotContain("Let me think")
                .shouldContain("The answer is 42.")
        }
    }

    @Test
    fun `non-streaming with --reasoning but no reasoning content in response`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"Just the answer."}}]}""")
                    .build()
            )

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--reasoning",
                )
            } finally {
                System.setOut(originalOut)
            }

            String(captured.toByteArray(), Charsets.UTF_8)
                .shouldNotContain("Reasoning")
                .shouldContain("Just the answer.")
        }
    }

    @Test
    fun `non-streaming with --reasoning and null content still produces output`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","reasoning_content":"Just the reasoning."}}]}""")
                    .build()
            )

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--reasoning",
                )
            } finally {
                System.setOut(originalOut)
            }

            String(captured.toByteArray(), Charsets.UTF_8)
                .shouldContain("Reasoning")
                .shouldContain("Just the reasoning.")
        }
    }

    @Test
    fun `streaming with --reasoning includes blockquote reasoning before content`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val sseBody = buildString {
            // reasoning deltas
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"reasoning_content":"Let me"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"reasoning_content":" think..."},"finish_reason":null}]}""")
            appendLine()
            // content deltas
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"content":"The"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"content":" answer"},"finish_reason":"stop"}]}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .headers(Headers.Builder().add("Content-Type", "text/event-stream").build())
                    .body(sseBody)
                    .build()
            )

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--stream",
                    "--reasoning",
                )
            } finally {
                System.setOut(originalOut)
            }

            val output = String(captured.toByteArray(), Charsets.UTF_8)
            output.shouldContain("> **Reasoning**")
                .shouldContain("Let me think...")
                .shouldContain("The answer")
            // reasoning before content
            output.indexOf("Reasoning") shouldBeLessThan output.indexOf("The answer")
        }
    }

    @Test
    fun `streaming without --reasoning skips reasoning deltas`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val sseBody = buildString {
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"reasoning_content":"secret reasoning"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"content":"Visible"},"finish_reason":"stop"}]}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .headers(Headers.Builder().add("Content-Type", "text/event-stream").build())
                    .body(sseBody)
                    .build()
            )

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--stream",
                )
            } finally {
                System.setOut(originalOut)
            }

            String(captured.toByteArray(), Charsets.UTF_8)
                .shouldNotContain("secret reasoning")
                .shouldNotContain("Reasoning")
                .shouldContain("Visible")
        }
    }

    @Test
    fun `streaming with --reasoning and multi-line reasoning produces valid blockquote`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val sseBody = buildString {
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"reasoning_content":"Line one\nLine two"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"content":"Answer"},"finish_reason":"stop"}]}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .headers(Headers.Builder().add("Content-Type", "text/event-stream").build())
                    .body(sseBody)
                    .build()
            )

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--stream",
                    "--reasoning",
                )
            } finally {
                System.setOut(originalOut)
            }

            // Each line of reasoning must be prefixed with "> "
            String(captured.toByteArray(), Charsets.UTF_8)
                .shouldContain("> Line one")
                .shouldContain("> Line two")
                .shouldContain("Answer")
        }
    }

    @Test
    fun `streaming with --reasoning and interleaved reasoning produces valid blockquotes`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val sseBody = buildString {
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"content":"Answer one"}}]}""")
            appendLine()
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"reasoning_content":"Reasoning one\nReasoning two"}}]}""")
            appendLine()
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"content":"Answer two"}}]}""")
            appendLine()
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"reasoning_content":"Reasoning three"},"finish_reason":"stop"}]}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .headers(Headers.Builder().add("Content-Type", "text/event-stream").build())
                    .body(sseBody)
                    .build()
            )

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--stream",
                    "--reasoning",
                )
            } finally {
                System.setOut(originalOut)
            }

            String(captured.toByteArray(), Charsets.UTF_8)
                .shouldContain("Answer one")
                .shouldContain("\nAnswer two") // with new line before second answer
                .shouldContain("> Reasoning one")
                .shouldContain("> Reasoning two")
                .shouldContain("> Reasoning three")
        }
    }

    @Test
    fun `--prompt-version none omits system message from request`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--prompt-version", "none",
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("\"user\"")
                        .shouldContain("code")
                        .shouldNotContain("\"system\"")
                }
            }
        }
    }

    @Test
    fun `--prompt-version none with custom prompt still omits system message`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")
        val promptFile = createTempFile("prompt", ".md")
        Files.write(promptFile, "My custom question\n".toByteArray())

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--prompt", promptFile.absolutePathString(),
                    "--prompt-version", "none",
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("My custom question")
                        .shouldNotContain("\"system\"")
                }
            }
        }
    }

    @Test
    fun `lang directive is included even with --prompt-version none`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--prompt-version", "none",
                    "--lang", "ru",
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("\"user\"")
                        .shouldContain("\"system\"")
                        .shouldContain("Respond entirely in Russian.")
                }
            }
        }
    }

    @Test
    fun `--print-args prints options to stderr with api-key masked`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            val originalOut = System.out
            val outBuf = ByteArrayOutputStream()
            val originalErr = System.err
            val errBuf = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
                System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "-k", "secret-key-123",
                    "--print-args",
                )
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
            }

            String(errBuf.toByteArray(), Charsets.UTF_8)
                .shouldContain("[ARGS]")
                .shouldContain("--dir = ${gitDir.absolutePathString()}")
                .shouldContain("--scope = ALL")
                .shouldContain("--model = test")
                .shouldContain("--api-key = ********")
                .shouldNotContain("secret-key-123")
                .shouldContain("--print-args = true")

            // stdout should still contain the LLM result
            String(outBuf.toByteArray(), Charsets.UTF_8)
                .shouldContain("ok")
        }
    }

    @Test
    fun `--print-args does not print when flag is absent`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            val originalErr = System.err
            val errBuf = ByteArrayOutputStream()
            try {
                System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                )
            } finally {
                System.setErr(originalErr)
            }

            String(errBuf.toByteArray(), Charsets.UTF_8)
                .shouldNotContain("[ARGS]")
        }
    }

    @Test
    fun `full run with --context appends context sections to user message`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val issueFile = createTempFile("issue", ".md")
        Files.write(issueFile, "Bug: NullPointerException at line 42\n".toByteArray())
        val traceFile = createTempFile("trace", ".txt")
        Files.write(traceFile, "java.lang.NullPointerException\n\tat com.Foo.bar(Foo.java:42)\n".toByteArray())

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            CommandLine(AidCommand()).execute(
                "-d", gitDir.absolutePathString(),
                "-m", "test",
                "-s", "all",
                "-u", llmServer.url("/").toString(),
                "--context", issueFile.absolutePathString(),
                "--context", traceFile.absolutePathString(),
            )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    val body = string(Charsets.UTF_8)
                    body.shouldContain("## CONTEXT: ${issueFile.fileName} ##")
                        .shouldContain("Bug: NullPointerException at line 42")
                        .shouldContain("## CONTEXT: ${traceFile.fileName} ##")
                        .shouldContain("java.lang.NullPointerException")
                    // Context must come after code
                    val codeIdx = body.indexOf("code")
                    codeIdx shouldNotBe -1
                    val ctxIdx = body.indexOf("## CONTEXT: ${issueFile.fileName} ##")
                    ctxIdx shouldBeGreaterThan codeIdx
                }
            }
        }
    }

    @Test
    fun `--context with custom prompt places context after prompt and code`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "some code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        val promptFile = createTempFile("prompt", ".md")
        Files.write(promptFile, "Explain this bug\n".toByteArray())
        val ctxFile = createTempFile("ctx", ".md")
        Files.write(ctxFile, "Stack trace here\n".toByteArray())

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            CommandLine(AidCommand()).execute(
                "-d", gitDir.absolutePathString(),
                "-m", "test",
                "-s", "all",
                "-u", llmServer.url("/").toString(),
                "--prompt", promptFile.absolutePathString(),
                "--context", ctxFile.absolutePathString(),
            )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    val body = string(Charsets.UTF_8)
                    val promptIdx = body.indexOf("Explain this bug")
                    promptIdx shouldNotBe -1
                    val codeIdx = body.indexOf("## CODE ##")
                    val ctxIdx = body.indexOf("## CONTEXT: ${ctxFile.fileName} ##")
                    // Order: prompt ? CODE ? CONTEXT
                    promptIdx shouldBeLessThan codeIdx
                    codeIdx shouldBeLessThan ctxIdx
                    body.shouldContain("Stack trace here")
                }
            }
        }
    }

    @Test
    fun `--context works with diff scope`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("App.java"), "public class App {}\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")
        Files.write(gitDir.resolve("App.java"), "public class App { int x; }\n".toByteArray())

        val ctxFile = createTempFile("design", ".md")
        Files.write(ctxFile, "Design doc content\n".toByteArray())

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            CommandLine(AidCommand()).execute(
                "-d", gitDir.absolutePathString(),
                "-m", "test",
                "-s", "diff",
                "-u", llmServer.url("/").toString(),
                "--context", ctxFile.absolutePathString(),
            )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("App.java")
                        .shouldContain("## CONTEXT: ${ctxFile.fileName} ##")
                        .shouldContain("Design doc content")
                }
            }
        }
    }

    @Test
    fun `interactive mode sends multi-turn conversation and exits on exit command`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            // Response for turn 1 (initial)
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"First response"}}]}""")
                    .build()
            )
            // Response for turn 2 (follow-up)
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"Second response"}}]}""")
                    .build()
            )

            val originalOut = System.out
            val outBuf = ByteArrayOutputStream()
            val originalErr = System.err
            val errBuf = ByteArrayOutputStream()
            val originalIn = System.`in`
            try {
                System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
                System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))
                System.setIn("Explain more\n/exit\n".byteInputStream())

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--interactive",
                )
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
                System.setIn(originalIn)
            }

            String(outBuf.toByteArray(), Charsets.UTF_8)
                .shouldContain("Turn 1")
                .shouldContain("First response")
                .shouldContain("Turn 2")
                .shouldContain("Second response")

            String(errBuf.toByteArray(), Charsets.UTF_8)
                .shouldContain("prompt>")

            llmServer.requestCount shouldBe 2
            llmServer.takeRequest(0, TimeUnit.SECONDS).shouldNotBeNull()

            // Second request should contain the full conversation history
            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("First response")
                        .shouldContain("Explain more")
                }
            }
        }
    }

    @Test
    fun `interactive mode exits on EOF`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"Only response"}}]}""")
                    .build()
            )

            val originalOut = System.out
            val outBuf = ByteArrayOutputStream()
            val originalIn = System.`in`
            try {
                System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
                // Empty input ? readLine() returns null immediately (EOF)
                System.setIn("".byteInputStream())

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--interactive",
                )
            } finally {
                System.setOut(originalOut)
                System.setIn(originalIn)
            }

            String(outBuf.toByteArray(), Charsets.UTF_8)
                .shouldContain("Only response")

            // Only 1 request (initial), no follow-up
            llmServer.requestCount shouldBe 1
        }
    }

    @Test
    fun `interactive mode with --stream streams each turn`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        fun sseBody(content: String): String = buildString {
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"role":"assistant","content":"$content"},"finish_reason":"stop"}]}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .headers(Headers.Builder().add("Content-Type", "text/event-stream").build())
                    .body(sseBody("Streamed first"))
                    .build()
            )
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .headers(Headers.Builder().add("Content-Type", "text/event-stream").build())
                    .body(sseBody("Streamed second"))
                    .build()
            )

            val originalOut = System.out
            val outBuf = ByteArrayOutputStream()
            val originalIn = System.`in`
            try {
                System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
                System.setIn("follow-up\n/exit\n".byteInputStream())

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--interactive",
                    "--stream",
                )
            } finally {
                System.setOut(originalOut)
                System.setIn(originalIn)
            }

            String(outBuf.toByteArray(), Charsets.UTF_8)
                .shouldContain("Turn 1")
                .shouldContain("Streamed first")
                .shouldContain("Turn 2")
                .shouldContain("Streamed second")

            llmServer.requestCount shouldBe 2
        }
    }

    @Test
    fun `interactive mode skips empty lines in REPL`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"R1"}}]}""")
                    .build()
            )
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"R2"}}]}""")
                    .build()
            )

            val originalIn = System.`in`
            try {
                // Empty lines should be skipped, only "real question" triggers a request
                System.setIn("\n\nreal question\n/exit\n".byteInputStream())

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--interactive",
                )
            } finally {
                System.setIn(originalIn)
            }

            // 2 requests: initial + "real question" (empty lines skipped)
            llmServer.requestCount shouldBe 2
        }
    }

    @Test
    fun `interactive mode includes system message in all requests`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"R1"}}]}""")
                    .build()
            )
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"R2"}}]}""")
                    .build()
            )

            val originalIn = System.`in`
            try {
                System.setIn("q\n/exit\n".byteInputStream())

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--interactive",
                )
            } finally {
                System.setIn(originalIn)
            }

            llmServer.requestCount shouldBe 2

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("\"system\"")
                }
            }

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("\"system\"")
                }
            }
        }
    }

    @Test
    fun `--dry-run takes precedence over --interactive`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--interactive",
                    "--dry-run",
                )
            } finally {
                System.setOut(originalOut)
            }

            // Dry-run output: JSON, no REPL
            String(captured.toByteArray(), Charsets.UTF_8)
                .shouldStartWith("{")
                .shouldContain("\"messages\"")
                .shouldNotContain("Turn")

            // No LLM call was made
            llmServer.requestCount shouldBe 0
        }
    }

    @Test
    fun `--max-turns stops the interaction`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"R1"}}]}""")
                    .build()
            )
            llmServer.enqueue(
                MockResponse.Builder().code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"R2"}}]}""")
                    .build()
            )

            val originalIn = System.`in`
            try {
                System.setIn("prompt_1\nprompt_2\n".byteInputStream())

                CommandLine(AidCommand()).execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--interactive",
                    "--max-turns", "2",
                )
            } finally {
                System.setIn(originalIn)
            }

            llmServer.requestCount shouldBe 2
            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldNotContain("prompt_")
                }
            }
            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("prompt_1")
                        .shouldNotContain("prompt_2")
                }
            }
        }
    }

    @Test
    fun `full run with sampling params includes them in request body`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                    "--temperature", "0.1",
                    "--max-tokens", "4096",
                    "--top-p", "0.9",
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    string(Charsets.UTF_8)
                        .shouldContain("\"temperature\":0.1")
                        .shouldContain("\"max_tokens\":4096")
                        .shouldContain("\"top_p\":0.9")
                }
            }
        }
    }

    @Test
    fun `full run without sampling params omits them from request body`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()
            llmServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""")
                    .build()
            )

            CommandLine(AidCommand())
                .execute(
                    "-d", gitDir.absolutePathString(),
                    "-m", "test",
                    "-s", "all",
                    "-u", llmServer.url("/").toString(),
                )

            llmServer.takeRequest(0, TimeUnit.SECONDS) shouldNotBeNull {
                body shouldNotBeNull {
                    val body = string(Charsets.UTF_8)
                    body.shouldNotContain("temperature")
                        .shouldNotContain("max_tokens")
                        .shouldNotContain("top_p")
                }
            }
        }
    }

    @Test
    fun `dry-run with sampling params shows them in JSON`() {
        val gitDir = createTempDirectory("aid-test-")
        gitDir.runProcess("git", "init")
        Files.write(gitDir.resolve("file.txt"), "code\n".toByteArray())
        gitDir.runProcess("git", "add", ".")
        gitDir.runProcess("git", "commit", "-m", "init")

        MockWebServer().use { llmServer ->
            llmServer.start()

            val originalOut = System.out
            val captured = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(captured, true, Charsets.UTF_8))

                CommandLine(AidCommand())
                    .execute(
                        "-d", gitDir.absolutePathString(),
                        "-m", "test",
                        "-s", "all",
                        "-u", llmServer.url("/").toString(),
                        "--dry-run",
                        "--temperature", "0.7",
                        "--max-tokens", "2048",
                    )
            } finally {
                System.setOut(originalOut)
            }

            String(captured.toByteArray(), Charsets.UTF_8)
                .shouldContain("\"temperature\": 0.7")
                .shouldContain("\"max_tokens\": 2048")
                .shouldNotContain("top_p")

            llmServer.requestCount shouldBe 0
        }
    }
}
