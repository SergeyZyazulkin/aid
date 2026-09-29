package dev.sz.aid

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.PrintStream
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

    @Test
    fun `readInteractivePrompt returns trimmed input line`() {
        val originalIn = System.`in`
        try {
            System.setIn("  hello world  \n".byteInputStream())
            Prompts.readInteractivePrompt() shouldBe "hello world"
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun `readInteractivePrompt returns null on EOF`() {
        val originalIn = System.`in`
        try {
            System.setIn("".byteInputStream())
            Prompts.readInteractivePrompt() shouldBe null
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun `readInteractivePrompt returns null on exit command`() {
        val originalIn = System.`in`
        try {
            System.setIn("/exit\n".byteInputStream())
            Prompts.readInteractivePrompt() shouldBe null
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun `readInteractivePrompt skips empty lines and returns next non-empty line`() {
        val originalIn = System.`in`
        val originalErr = System.err
        val errBuf = ByteArrayOutputStream()
        try {
            System.setIn("\n\nactual question\n".byteInputStream())
            System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))
            Prompts.readInteractivePrompt() shouldBe "actual question"
        } finally {
            System.setIn(originalIn)
            System.setErr(originalErr)
        }
        val prompts = String(errBuf.toByteArray(), Charsets.UTF_8)
            .split("prompt> ").size - 1
        prompts shouldBe 3
    }

    @Test
    fun `readInteractivePrompt skips whitespace-only lines`() {
        val originalIn = System.`in`
        val originalErr = System.err
        val errBuf = ByteArrayOutputStream()
        try {
            System.setIn("   \n\t\nreal input\n".byteInputStream())
            System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))
            Prompts.readInteractivePrompt() shouldBe "real input"
        } finally {
            System.setIn(originalIn)
            System.setErr(originalErr)
        }
        val prompts = String(errBuf.toByteArray(), Charsets.UTF_8)
            .split("prompt> ").size - 1
        prompts shouldBe 3
    }

    @Test
    fun `readInteractivePrompt returns null on exit command after empty lines`() {
        val originalIn = System.`in`
        try {
            System.setIn("\n  \n/exit\n".byteInputStream())
            Prompts.readInteractivePrompt() shouldBe null
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun `readInteractivePrompt prints prompt to stderr for each attempt`() {
        val originalIn = System.`in`
        val originalErr = System.err
        val errBuf = ByteArrayOutputStream()
        try {
            System.setIn("first\n".byteInputStream())
            System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))
            Prompts.readInteractivePrompt() shouldBe "first"
        } finally {
            System.setIn(originalIn)
            System.setErr(originalErr)
        }
        String(errBuf.toByteArray(), Charsets.UTF_8)
            .shouldContain("prompt> ")
    }
}