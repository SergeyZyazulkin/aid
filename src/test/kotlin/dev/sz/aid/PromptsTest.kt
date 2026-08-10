package dev.sz.aid

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.test.Test

class PromptsTest {

    @Test
    fun `review prompt loads from resources`() {
        Prompts.review.isNotEmpty() shouldBe true
    }

    @Test
    fun `custom prompt loads from resources`() {
        Prompts.custom.isNotEmpty() shouldBe true
    }

    @Test
    fun `readUserPrompt fails on missing file`() {
        assertThrows<IllegalArgumentException> {
            Prompts.readUserPrompt("/nonexistent/file.md")
        }.message?.contains("Prompt file does not exist") shouldBe true
    }

    @Test
    fun `readUserPrompt reads and trims file content`() {
        val tempFile = createTempFile(suffix = ".md")
        Files.write(tempFile, "  Hello world!  \n".toByteArray())
        Prompts.readUserPrompt(tempFile.absolutePathString()) shouldBe "Hello world!"
    }
}