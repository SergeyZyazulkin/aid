package dev.sz.aid

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
        every { anyConstructed<Git>().diff(any(), any()) } returns expectedDiff

        CodeProvider(gitDir, codeLimit = 1_000_000)
            .collectDiff("HEAD~1", emptyList()) shouldBe expectedDiff
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
        Files.write(gitDir.resolve("big.txt"), "0".repeat(100).toByteArray())

        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles(any()) } returns listOf("big.txt")

        assertThrows<IllegalStateException> {
            CodeProvider(gitDir, codeLimit = 10).collectAll()
        }.message.shouldContain("Code exceeds 10 characters")
    }

    @Test
    fun `collect enforces total size limit`() {
        Files.write(gitDir.resolve("file1.txt"), "1".repeat(50).toByteArray())
        Files.write(gitDir.resolve("file2.txt"), "2".repeat(100).toByteArray())

        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles(any()) } returns listOf("file1.txt", "file2.txt")

        assertThrows<IllegalStateException> {
            CodeProvider(gitDir, codeLimit = 125).collectAll()
        }.message.shouldContain("Code exceeds 125 characters")
    }

    @Test
    fun `collectFiles collects files from specified paths`() {
        val sourceDir: Path = gitDir.resolve("src/main/kotlin")
        Files.createDirectories(sourceDir)
        Files.write(sourceDir.resolve("Main.kt"), "fun main() {}".toByteArray())
        Files.write(gitDir.resolve("README.md"), "# Project".toByteArray())

        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles(any()) } returns listOf("src/main/kotlin/Main.kt")

        val provider = CodeProvider(gitDir, codeLimit = 1_000_000)
        val result = provider.collectFiles(listOf("src/main/kotlin"))
        result.shouldContain("fun main() {}")
        result.shouldNotContain("# Project")
    }

    @Test
    fun `collectFiles fails on non-existent specified directory`() {
        val nonExistent = Paths.get("nonexistent", "path").toString()
        assertThrows<IllegalStateException> {
            CodeProvider(gitDir, codeLimit = 1_000).collectFiles(listOf(nonExistent))
        }
    }

    @Test
    fun `collectFiles rejects escaping paths`() {
        assertThrows<IllegalStateException> {
            CodeProvider(gitDir, codeLimit = 10).collectFiles(listOf(".."))
        }.message.shouldContain("outside repository")
    }

    @Test
    fun `collectFiles with invalid glob pattern throws descriptive error`() {
        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles(any()) } returns listOf("src/Foo.java")

        assertThrows<IllegalStateException> {
            CodeProvider(gitDir, codeLimit = 100)
                .collectFiles(emptyList(), listOf("[abc.java"))
        }.message.shouldContain("Invalid --filter glob pattern '[abc.java'")
    }

    @Test
    fun `collectFiles with filter includes only matching files from a specific directory`() {
        Files.write(gitDir.resolve("Baz.java"), "public class Baz {}".toByteArray())
        val srcDir = gitDir.resolve("src")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Foo.java"), "public class Foo {}".toByteArray())
        Files.write(srcDir.resolve("Bar.kt"), "fun bar() {}".toByteArray())

        mockkConstructor(Git::class)
        every {
            anyConstructed<Git>().listTextFiles(any())
        } returns listOf("Baz.java", "src/Foo.java", "src/Bar.kt")

        CodeProvider(gitDir, codeLimit = 100_000)
            .collectFiles(emptyList(), listOf("src/*.java"))
            .shouldContain("Foo.java")
            .shouldContain("public class Foo {}")
            .shouldNotContain("Baz.java")
            .shouldNotContain("public class Baz {}")
            .shouldNotContain("Bar.kt")
            .shouldNotContain("fun bar() {}")
    }

    @Test
    fun `collectFiles with filter includes matching files from any directory`() {
        Files.write(gitDir.resolve("Baz.java"), "public class Baz {}".toByteArray())
        val srcDir = gitDir.resolve("src")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Foo.java"), "public class Foo {}".toByteArray())
        Files.write(srcDir.resolve("Bar.kt"), "fun bar() {}".toByteArray())

        mockkConstructor(Git::class)
        every {
            anyConstructed<Git>().listTextFiles(any())
        } returns listOf("Baz.java", "src/Foo.java", "src/Bar.kt")

        CodeProvider(gitDir, codeLimit = 100_000)
            .collectFiles(emptyList(), listOf("**.java"))
            .shouldContain("Foo.java")
            .shouldContain("public class Foo {}")
            .shouldContain("Baz.java")
            .shouldContain("public class Baz {}")
            .shouldNotContain("Bar.kt")
            .shouldNotContain("fun bar() {}")
    }

    @Test
    fun `collectFiles with multiple filters uses OR logic`() {
        val srcDir = gitDir.resolve("src")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Foo.java"), "java code".toByteArray())
        Files.write(srcDir.resolve("Bar.kt"), "kotlin code".toByteArray())
        Files.write(srcDir.resolve("baz.py"), "python code".toByteArray())

        mockkConstructor(Git::class)
        every {
            anyConstructed<Git>().listTextFiles(any())
        } returns listOf("src/Foo.java", "src/Bar.kt", "src/baz.py")

        CodeProvider(gitDir, codeLimit = 10_000)
            .collectFiles(emptyList(), listOf("src/*.java", "**.kt"))
            .shouldContain("Foo.java")
            .shouldContain("Bar.kt")
            .shouldNotContain("baz.py")
    }

    @Test
    fun `collectFiles with no filter includes everything`() {
        val srcDir = gitDir.resolve("src")
        Files.createDirectories(srcDir)
        Files.write(srcDir.resolve("Foo.java"), "java".toByteArray())
        Files.write(srcDir.resolve("notes.txt"), "text".toByteArray())

        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles(any()) } returns listOf("src/Foo.java", "src/notes.txt")

        CodeProvider(gitDir, codeLimit = 1_000)
            .collectFiles(emptyList(), emptyList())
            .shouldContain("Foo.java")
            .shouldContain("notes.txt")
    }

    @Test
    fun `collectFiles with filter matching nothing returns empty`() {
        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles(any()) } returns listOf("src/Foo.java", "src/Bar.kt")

        CodeProvider(gitDir, codeLimit = 100)
            .collectFiles(emptyList(), listOf("**.xml")) shouldBe ""
    }

    @Test
    fun `collectDiff delegates pathspecs to git`() {
        val expectedDiff = "diff with filters"
        mockkConstructor(Git::class)
        every { anyConstructed<Git>().diff(any(), any()) } returns expectedDiff

        CodeProvider(gitDir, codeLimit = 1_000_000)
            .collectDiff("HEAD~1", listOf("src/", ":(exclude)*.min.js")) shouldBe expectedDiff

        verify { anyConstructed<Git>().diff("HEAD~1", listOf("src/", ":(exclude)*.min.js")) }
    }

    @Test
    fun `collectFiles accepts git magic pathspecs`() {
        val sourceDir: Path = gitDir.resolve("src/main/kotlin")
        Files.createDirectories(sourceDir)
        Files.write(sourceDir.resolve("App.kt"), "fun main() {}".toByteArray())

        mockkConstructor(Git::class)
        every { anyConstructed<Git>().listTextFiles(any()) } returns listOf("src/main/kotlin/App.kt")

        CodeProvider(gitDir, codeLimit = 10_000)
            .collectFiles(listOf(":(exclude)build/"), emptyList())
            .shouldContain("src/main/kotlin/App.kt")
    }
}
