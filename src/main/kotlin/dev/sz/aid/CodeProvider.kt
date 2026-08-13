package dev.sz.aid

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.fileSize
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

class CodeProvider(dir: Path, val codeLimit: Int) {

    private val git: Git = Git(dir)

    init {
        require(codeLimit > 0) { "Code limit must be positive: $codeLimit" }
    }

    fun collectDiff(commit: String): String {
        return git.diff(commit)
            .also { diff -> check(diff.length <= codeLimit) { "Diff (${diff.length}) exceeds $codeLimit characters" } }
    }

    fun collectAll(): String = collectFiles(emptyList())

    /**
     * [paths] defines paths in the project to collect from.
     * Collects everything from the project on empty [paths].
     */
    fun collectFiles(paths: List<String>): String {
        val code = StringBuilder()
        val basePath: Path = git.dir.toAbsolutePath().normalize().toRealPath()
        val resolvedPaths = basePath.resolveRelatively(paths)

        git.listTextFiles(resolvedPaths)
            .forEach { file ->
                val filePath: Path = basePath.resolve(Paths.get(file)).normalize().toRealPath()
                require(filePath.startsWith(basePath)) { "$file escapes project directory ${git.dir}" }

                // to avoid reading huge files
                // max UTF-8 char length is 4 bytes
                check(filePath.fileSize() / 4 <= codeLimit - code.length) { "Code exceeds $codeLimit characters" }

                code.appendLine("=== $file ===")
                    .appendLine(filePath.readText(Charsets.UTF_8))

                check(code.length <= codeLimit) { "Code exceeds $codeLimit characters" }
            }

        return code.toString()
    }

    private fun Path.resolveRelatively(paths: List<String>): List<String> {
        return paths.map { strPath ->
            require(strPath.isNotBlank()) { "Blank path: '$strPath'" }
            val resolvedAbsolute = resolve(strPath).toAbsolutePath().normalize().toRealPath()
            require(resolvedAbsolute.startsWith(this)) {
                "Path '$strPath' ($resolvedAbsolute) escapes ${absolutePathString()}"
            }
            resolvedAbsolute.relativeTo(this).toString()
        }
    }
}