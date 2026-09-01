package dev.sz.aid

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class Git(val dir: Path) {

    init {
        require(dir.exists()) { "Target directory does not exist: $dir" }
        val gitConfig: Path = dir.resolve(".git").resolve("config")
        require(Files.isRegularFile(gitConfig)) { "Missing git config in $dir" }
    }

    fun diff(commit: String, pathspecs: List<String> = emptyList(), contextLines: Int? = null): String {
        require(contextLines == null || contextLines >= 0) { "Context lines must be non-negative: $contextLines" }

        val command = buildList {
            add("git")
            add("diff")
            contextLines?.let { add("-U$it") }
            add(commit)
        }.withPathspecs(pathspecs)

        return dir.runProcess(*command)
    }

    fun listTextFiles(): List<String> = listTextFiles(emptyList())

    fun listTextFiles(pathspecs: List<String>): List<String> {
        // git ls-files can't filter out binary files
        val command = listOf("git", "grep", "-Il", ".").withPathspecs(pathspecs)

        return dir.runProcess(*command)
            .lines()
            .filter { it.isNotBlank() }
    }

    private fun List<String>.withPathspecs(pathspecs: List<String>): Array<String> =
        if (pathspecs.isEmpty()) {
            this
        } else {
            this + listOf("--") + pathspecs
        }.toTypedArray()
}
