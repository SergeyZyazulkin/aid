package dev.sz.aid

import java.nio.file.Paths
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists

private const val BASE_PROMPT_PATH = "/prompts"
private const val DIRECTIVE_DIR = "directives"

object Prompts {

    val review: String by lazy { readSystemPrompt("review.md") }
    val custom: String by lazy { readSystemPrompt("custom.md") }

    fun readUserPrompt(strPath: String): String {
        val path = Paths.get(strPath)
        require(path.exists()) { "Prompt file does not exist: $strPath" }

        return path.bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
            .trim()
    }

    fun readSystemDirective(name: String): String = readSystemPrompt("$DIRECTIVE_DIR/$name")

    private fun readSystemPrompt(name: String): String = javaClass
        .getResourceAsStream("$BASE_PROMPT_PATH/$name")
        ?.bufferedReader(Charsets.UTF_8)
        ?.use { it.readText() }
        ?.trim()
        ?: error("Missing prompt resource: $name")
}
