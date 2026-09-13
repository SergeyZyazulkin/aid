package dev.sz.aid

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.test.Test

class PromptsTest {

    @Test
    fun `review prompts load from resources`() {
        for (version in Prompts.Version.entries) {
            val result = version.reviewPrompt()
            if (version == Prompts.Version.NONE) {
                result.shouldBeNull()
            } else {
                result.shouldNotBeEmpty()
            }
        }
    }

    @Test
    fun `custom prompts load from resources`() {
        for (version in Prompts.Version.entries) {
            val result = version.customPrompt()
            if (version == Prompts.Version.NONE) {
                result.shouldBeNull()
            } else {
                result.shouldNotBeEmpty()
            }
        }
    }

    @Test
    fun `readUserPrompt fails on missing file`() {
        assertThrows<IllegalArgumentException> {
            Prompts.readUserPrompt("/nonexistent/file.md")
        }.message.shouldContain("Prompt file does not exist")
    }

    @Test
    fun `readUserPrompt reads and trims file content`() {
        val tempFile = createTempFile(suffix = ".md")
        Files.write(tempFile, "  Hello world!  \n".toByteArray())
        Prompts.readUserPrompt(tempFile.absolutePathString()) shouldBe "Hello world!"
    }
}