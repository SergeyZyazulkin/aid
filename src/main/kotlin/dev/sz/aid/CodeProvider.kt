package dev.sz.aid

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.util.regex.PatternSyntaxException
import kotlin.io.path.fileSize
import kotlin.io.path.readText

// All pathspec parameters in this class are raw Git pathspecs.
// They are passed verbatim to Git commands and must NOT be
// resolved, normalized, or otherwise transformed as filesystem paths.
class CodeProvider(dir: Path, val codeLimit: Int) {

    private val git: Git = Git(dir)

    init {
        require(codeLimit > 0) { "Code limit must be positive: $codeLimit" }
    }

    /**
     * [pathspecs] are Git pathspecs defining files included in diff.
     * Full diff on empty [pathspecs].
     */
    fun collectDiff(commit: String, pathspecs: List<String>): String {
        return git.diff(commit, pathspecs)
            .also { diff -> check(diff.length <= codeLimit) { "Diff (${diff.length}) exceeds $codeLimit characters" } }
    }

    fun collectAll(filters: List<String> = emptyList()): String = collectFiles(emptyList(), filters)

    /**
     * [pathspecs] are Git pathspecs defining files in the project to collect from.
     * Collects everything from the project on empty [pathspecs].
     * [filters] are glob patterns matched against the file paths returned by Git.
     * When non-empty, only files matching at least one pattern are included.
     */
    fun collectFiles(pathspecs: List<String>, filters: List<String> = emptyList()): String {
        val code = StringBuilder()
        val basePath: Path = git.dir.toAbsolutePath().normalize().toRealPath()
        val fileMatchers: List<PathMatcher> = filters.toPathMatchers()

        git.listTextFiles(pathspecs)
            .filter { file -> fileMatchers.matchesAny(file) }
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

    private fun List<String>.toPathMatchers(): List<PathMatcher> = map { pattern ->
        try {
            FileSystems.getDefault()
                .getPathMatcher("glob:$pattern")
        } catch (e: PatternSyntaxException) {
            error("Invalid --filter glob pattern '$pattern': ${e.message}")
        }
    }

    private fun List<PathMatcher>.matchesAny(path: String): Boolean =
        isEmpty() || any { it.matches(Paths.get(path)) }
}
