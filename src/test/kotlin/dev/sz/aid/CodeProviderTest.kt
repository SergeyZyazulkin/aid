package dev.sz.aid

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.test.AfterTest
import kotlin.test.Test

class CodeProviderTest {

    @AfterTest
    fun cleanup() = unmockkAll()

    private val gitDir: Path by lazy {
        createTempDirectory("aid-test-")
            .also { it.runProcess("git", "init") }
    }

    @Test
    fun `collectDiff delegates to git`() {
        val expectedDiff = "some random diff"

        mockkConstructor(Git::class)
        every { anyConstructed<Git>().diff(any()) } returns expectedDiff

        val provider = CodeProvider(gitDir, codeLimit = 1_000_000)
        provider.collectDiff("HEAD~1") shouldBe expectedDiff
    }

    @Test
    fun `collect fails on escaping path`() {
        val secretFile = createTempFile("secret")

        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles(any()) } returns listOf("../${secretFile.name}")

        assertThrows<IllegalArgumentException> {
            CodeProvider(gitDir, codeLimit = 10_000).collectAll()
        }.message.shouldContain("escapes project directory")
    }

    @Test
    fun `collect enforces size limit per file`() {
        val bigFile = gitDir.resolve("big.txt")
        Files.write(bigFile, "0".repeat(100).toByteArray())

        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles(any()) } returns listOf(bigFile.name)

        assertThrows<IllegalStateException> {
            CodeProvider(gitDir, codeLimit = 10).collectAll()
        }.message.shouldContain("Code exceeds 10 characters")
    }

    @Test
    fun `collect enforces total size limit`() {
        val firstFile = gitDir.resolve("file1.txt")
        Files.write(firstFile, "1".repeat(50).toByteArray())
        val secondFile = gitDir.resolve("file2.txt")
        Files.write(secondFile, "2".repeat(100).toByteArray())

        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles(any()) } returns listOf(firstFile.name, secondFile.name)

        assertThrows<IllegalStateException> {
            CodeProvider(gitDir, codeLimit = 125).collectAll()
        }.message.shouldContain("Code exceeds 125 characters")
    }

    @Test
    fun `collectFiles collects files from specified paths`() {
        val sourceDir: Path = gitDir.resolve("src/main/kotlin")
        val sourcePathStr = sourceDir.relativeTo(gitDir).toString()
        Files.createDirectories(sourceDir)
        val main: Path = sourceDir.resolve("Main.kt")
        Files.write(main, "fun main() {}".toByteArray())
        Files.write(gitDir.resolve("README.md"), "# Project".toByteArray())

        mockkConstructor(Git::class)
        every {
            anyConstructed<Git>().listTextFiles(any())
        } returns listOf(main.relativeTo(gitDir).toString())

        val provider = CodeProvider(gitDir, codeLimit = 1_000_000)
        val result = provider.collectFiles(listOf(sourcePathStr))
        result.shouldContain("fun main() {}")
        result.shouldNotContain("# Project")
    }

    @Test
    fun `collectFiles fails on non-existent specified directory`() {
        val nonExistent = Paths.get("nonexistent", "path").toString()
        assertThrows<NoSuchFileException> {
            CodeProvider(gitDir, codeLimit = 1_000).collectFiles(listOf(nonExistent))
        }
    }

    @Test
    fun `collectFiles rejects escaping paths`() {
        assertThrows<IllegalArgumentException> {
            CodeProvider(gitDir, codeLimit = 10).collectFiles(listOf(".."))
        }.message.shouldContain("Path '..'")
            .shouldContain("escapes")
    }
}