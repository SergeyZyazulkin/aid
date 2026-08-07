package dev.sz.aid

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.fileSize
import kotlin.io.path.readText

class CodeProvider(dir: Path, val codeLimit: Int) {

    private val git: Git = Git(dir)

    init {
        require(codeLimit > 0) { "Code limit must be positive: $codeLimit" }
    }

    fun collect(scope: CodeScope = CodeScope.DIFF): String = when (scope) {
        CodeScope.DIFF -> collectDiffHead()
        CodeScope.ALL -> collectAll()
    }

    private fun collectDiffHead(): String {
        return git.diffHead()
            .also { diff -> check(diff.length <= codeLimit) { "Diff (${diff.length}) exceeds $codeLimit characters" } }
    }

    private fun collectAll(): String {
        val code = StringBuilder()
        val basePath: Path = git.dir.toAbsolutePath().normalize().toRealPath()

        git.listTextFiles()
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
}