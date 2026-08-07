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

    fun diff(commit: String): String {
        return dir.runProcess("git", "diff", commit)
    }

    fun listTextFiles(): List<String> {
        // git ls-files can't filter out binary files
        return dir.runProcess("git", "grep", "-Il", ".")
            .lines()
            .filter { it.isNotBlank() }
    }
}