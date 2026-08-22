package dev.sz.aid

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
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
                    .body("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"отлично\"}}]}")
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
                    .body("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}")
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
                .shouldContain("Completed in ")
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
}
