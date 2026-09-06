package dev.sz.aid

import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test

class ResultWriterTest {

    @Test
    fun `formats multi-line reasoning as blockquote`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)
        ResultWriter(out).apply {
            writeReasoning("Line one\nLine two")
            writeContent("Answer")
            finish()
        }
        String(buf.toByteArray(), Charsets.UTF_8).lines()
            .shouldBe(listOf(
                "> **Reasoning**",
                ">",
                "> Line one",
                "> Line two",
                "",
                "Answer",
                "",
            ))
    }

    @Test
    fun `interleaved reasoning and content produce valid blockquotes`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)
        ResultWriter(out).apply {
            writeReasoning("Reasoning 1")
            writeContent("Answer 1")
            writeReasoning("Reasoning 2")
            writeContent("Answer 2")
            writeReasoning("Reasoning 3")
            finish()
        }
        String(buf.toByteArray(), Charsets.UTF_8).lines()
            .shouldBe(listOf(
                "> **Reasoning**",
                ">",
                "> Reasoning 1",
                "",
                "Answer 1",
                "",
                "> **Reasoning**",
                ">",
                "> Reasoning 2",
                "",
                "Answer 2",
                "",
                "> **Reasoning**",
                ">",
                "> Reasoning 3",
                "",
            ))
    }

    @Test
    fun `content-only output has no reasoning header`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)
        ResultWriter(out).apply {
            writeContent("Hello")
            finish()
        }
        String(buf.toByteArray(), Charsets.UTF_8).lines()
            .shouldBe(listOf("Hello", ""))
    }

    @Test
    fun `finish with no writes produces nothing`() {
        val buf = ByteArrayOutputStream()
        val out = PrintStream(buf, true, Charsets.UTF_8)
        ResultWriter(out).apply {
            finish()
        }
        String(buf.toByteArray(), Charsets.UTF_8)
            .shouldBe("")
    }
}
