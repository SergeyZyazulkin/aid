package dev.sz.aid

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.uuid.Uuid

class GitTest {

    @AfterTest
    fun cleanup() = unmockkAll()

    @Test
    fun `requires existing directory`() {
        val notExistingDir = createTempDirectory("aid-test-")
            .resolve("not-existing-directory")

        assertThrows<IllegalArgumentException> {
            Git(notExistingDir)
        }.message.shouldContain("Target directory does not exist")
    }

    @Test
    fun `requires git config to exist`() {
        val fakeGitDir = createTempDirectory("fake-git")
        Files.createDirectories(fakeGitDir.resolve(".git")) // config missing
        assertThrows<IllegalArgumentException> {
            Git(fakeGitDir)
        }.message.shouldContain("Missing git config")
    }

    @Test
    fun `diff calls git diff with commit ref`() {
        val tempGitDir = createTempDirectory("aid-test-git-")
        tempGitDir.runProcess("git", "init")

        mockkStatic(Path::runProcess)
        every { tempGitDir.runProcess(*anyVararg()) } returns ""

        val git = Git(tempGitDir)
        val ref = Uuid.random().toString()
        git.diff(ref)

        verify { tempGitDir.runProcess("git", "diff", ref) }
    }

    @Test
    fun `list calls git grep and filters blank lines`() {
        val tempGitDir = createTempDirectory("aid-test-git-")
        tempGitDir.runProcess("git", "init")

        mockkStatic(Path::runProcess)
        every { tempGitDir.runProcess(*anyVararg()) } returns "\n \ntest\n\ntest2\n\n   "

        Git(tempGitDir).listTextFiles() shouldBe listOf("test", "test2")
        verify { tempGitDir.runProcess("git", "grep", "-Il", ".") }
    }
}