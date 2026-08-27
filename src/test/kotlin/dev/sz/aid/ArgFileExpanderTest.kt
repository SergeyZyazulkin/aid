package dev.sz.aid

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test

class ArgFileExpanderTest {

    @Test
    fun `no @ tokens returns args unchanged`() {
        val args = arrayOf("-d", "/repo", "-m", "llama3")
        expandArgFiles(args) shouldBe args
    }

    @Test
    fun `single @file expands to its tokens`() {
        val file = createTempFile("args", ".txt")
        file.writeText("-d /repo\n-m llama3\n--stream\n")

        val result = expandArgFiles(arrayOf("@$file"))
        result shouldBe arrayOf("-d", "/repo", "-m", "llama3", "--stream")
    }

    @Test
    fun `mix of inline args and @file`() {
        val file = createTempFile("args", ".txt")
        file.writeText("-s all\n-f **.java\n")

        val result = expandArgFiles(arrayOf("-d", "/repo", "@$file", "-m", "llama3"))
        result shouldBe arrayOf("-d", "/repo", "-s", "all", "-f", "**.java", "-m", "llama3")
    }

    @Test
    fun `multiple @file tokens are all expanded`() {
        val file1 = createTempFile("args1", ".txt")
        file1.writeText("-d /repo\n")
        val file2 = createTempFile("args2", ".txt")
        file2.writeText("-m llama3\n--stream\n")

        val result = expandArgFiles(arrayOf("@$file1", "@$file2"))
        result shouldBe arrayOf("-d", "/repo", "-m", "llama3", "--stream")
    }

    @Test
    fun `blank lines and comments are ignored`() {
        val file = createTempFile("args", ".txt")
        file.writeText("# this is a comment\n\n-d /repo\n   \n# another comment\n-m llama3\n")

        val result = expandArgFiles(arrayOf("@$file"))
        result shouldBe arrayOf("-d", "/repo", "-m", "llama3")
    }

    @Test
    fun `multiple tokens on one line are split`() {
        val file = createTempFile("args", ".txt")
        file.writeText("-d /repo -m=llama3\n--stream\n")

        val result = expandArgFiles(arrayOf("@$file"))
        result shouldBe arrayOf("-d", "/repo", "-m=llama3", "--stream")
    }

    @Test
    fun `empty file produces no args`() {
        val file = createTempFile("args", ".txt")
        file.writeText("")

        val result = expandArgFiles(arrayOf("@$file"))
        result shouldBe emptyArray()
    }

    @Test
    fun `file with only comments produces no args`() {
        val file = createTempFile("args", ".txt")
        file.writeText("# only a comment\n# another\n")

        val result = expandArgFiles(arrayOf("@$file"))
        result shouldBe emptyArray()
    }

    @Test
    fun `missing file throws with clear message`() {
        val ex = assertThrows<IllegalArgumentException> {
            expandArgFiles(arrayOf("@/nonexistent/args.txt"))
        }
        ex.message shouldContain "Arguments file not found: /nonexistent/args.txt"
    }

    @Test
    fun `directory instead of file throws`() {
        val dir = createTempDirectory("aid-args-test-")
        val ex = assertThrows<IllegalArgumentException> {
            expandArgFiles(arrayOf("@$dir"))
        }
        ex.message shouldContain "Not a regular file"
    }

    @Test
    fun `bare @ is treated as literal arg`() {
        val result = expandArgFiles(arrayOf("@"))
        result shouldBe arrayOf("@")
    }

    @Test
    fun `@file with trailing whitespace on lines`() {
        val file = createTempFile("args", ".txt")
        file.writeText("  -d /repo  \n  -m llama3  \n")

        val result = expandArgFiles(arrayOf("@$file"))
        result shouldBe arrayOf("-d", "/repo", "-m", "llama3")
    }

    @Test
    fun `handles CRLF line endings`() {
        val file = createTempFile("args", ".txt")
        file.writeText("-d /repo\r\n-m llama3\r\n")

        val result = expandArgFiles(arrayOf("@$file"))
        result shouldBe arrayOf("-d", "/repo", "-m", "llama3")
    }

    @Test
    fun `@@ produces literal @`() {
        val result = expandArgFiles(arrayOf("@@foo"))
        result shouldBe arrayOf("@foo")
    }

    @Test
    fun `BOM is ignored`() {
        val file = createTempFile("args", ".txt")
        file.writeText("\uFEFF  -d=/repo  \n  --model=llama3  \n")

        val result = expandArgFiles(arrayOf("@$file"))
        result shouldBe arrayOf("-d=/repo", "--model=llama3")
    }

    @Test
    fun `@token inside a file is not recursively expanded`() {
        val inner = createTempFile("inner", ".txt")
        inner.writeText("-m llama3\n")
        val outer = createTempFile("outer", ".txt")
        outer.writeText("-d /repo\n@$inner\n")

        val result = expandArgFiles(arrayOf("@$outer"))
        result shouldBe arrayOf("-d", "/repo", "@$inner")
    }
}
