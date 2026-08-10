package dev.sz.aid

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.name
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
    fun `collectAll fails on escaping path`() {
        val secretFile = createTempFile("secret")

        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles() } returns listOf("../${secretFile.name}")

        assertThrows<IllegalArgumentException> {
            CodeProvider(gitDir, codeLimit = 10_000).collectAll()
        }.message?.contains("escapes project directory") shouldBe true
    }

    @Test
    fun `collectAll enforces size limit per file`() {
        val bigFile = gitDir.resolve("big.txt")
        Files.write(bigFile, "0".repeat(100).toByteArray())

        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles() } returns listOf(bigFile.name)

        assertThrows<IllegalStateException> {
            CodeProvider(gitDir, codeLimit = 10).collectAll()
        }.message?.contains("Code exceeds 10 characters") shouldBe true
    }

    @Test
    fun `collectAll enforces total size limit`() {
        val firstFile = gitDir.resolve("file1.txt")
        Files.write(firstFile, "1".repeat(50).toByteArray())
        val secondFile = gitDir.resolve("file2.txt")
        Files.write(secondFile, "2".repeat(100).toByteArray())

        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles() } returns listOf(firstFile.name, secondFile.name)

        assertThrows<IllegalStateException> {
            CodeProvider(gitDir, codeLimit = 125).collectAll()
        }.message?.contains("Code exceeds 125 characters") shouldBe true
    }
}