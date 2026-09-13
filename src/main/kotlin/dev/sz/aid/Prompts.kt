package dev.sz.aid

import picocli.CommandLine
import java.nio.file.Paths
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists

private const val BASE_PROMPT_PATH = "/prompts"
private const val DIRECTIVE_DIR = "directives"

object Prompts {

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

    enum class Version(val dir: String?) {
        V1("v1"),
        V2("v2"),
        NONE(null);

        fun reviewPrompt(): String? = readVersionedSystemPrompt("review.md")

        fun customPrompt(): String? = readVersionedSystemPrompt("custom.md")

        private fun readVersionedSystemPrompt(name: String): String? = if (dir != null) {
            readSystemPrompt("$dir/$name")
        } else {
            null
        }

        class Converter : CommandLine.ITypeConverter<Version> {
            override fun convert(value: String): Version = valueOf(value.uppercase())
        }
    }
}
