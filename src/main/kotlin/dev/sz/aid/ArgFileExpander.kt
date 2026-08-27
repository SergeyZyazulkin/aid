package dev.sz.aid

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

private val WHITESPACE = Regex("\\s+")

/**
 * Expands `@file` tokens in [args] by reading the referenced file and
 * replacing the token with whitespace-split tokens from each non-blank,
 * non-comment line.
 *
 * File format (one option/value per line, or multiple tokens per line):
 * ```
 * # comment
 * -d /path/to/repo
 * -m llama3
 * --stream
 * ```
 */
fun expandArgFiles(args: Array<String>): Array<String> {
    val result = mutableListOf<String>()
    for (arg in args) {
        if (arg.startsWith("@") && arg.length > 1) {
            if (arg.startsWith("@@")) {
                result.add(arg.removePrefix("@"))
            } else {
                val stringPath = arg.removePrefix("@")
                val filePath: Path = Paths.get(stringPath)
                require(filePath.exists()) { "Arguments file not found: $stringPath" }
                require(Files.isRegularFile(filePath)) { "Not a regular file: $stringPath" }
                result.addAll(readArgFile(filePath))
            }
        } else {
            result.add(arg)
        }
    }
    return result.toTypedArray()
}

private fun readArgFile(path: Path): List<String> {
    val content = Files.readString(path, Charsets.UTF_8)
        .removePrefix("\uFEFF") // remove BOM

    return content.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .flatMap { it.split(WHITESPACE) }
        .filter { it.isNotEmpty() }
}
