package dev.sz

import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

fun Path.runProcess(vararg command: String): String = runBlocking {
    // can't merge stderr into stdout because Git puts warnings
    // and other messages into stderr even when the exit code is 0
    val process = ProcessBuilder(*command)
        .directory(toFile())
        .redirectErrorStream(false)
        .start()

    try {
        withTimeout(60.seconds) {
            // can't read streams sequentially as they may block independently due to the full buffer
            val stdoutDeferred: Deferred<String> = async(Dispatchers.IO) {
                process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val stderrDeferred: Deferred<String> = async(Dispatchers.IO) {
                process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }

            val exitCode: Int = withContext(Dispatchers.IO) { process.waitFor() }
            val (stdout, stderr) = awaitAll(stdoutDeferred, stderrDeferred)

            if (exitCode != 0) {
                throw IllegalStateException("${command.contentToString()} failed with exit code $exitCode: $stderr")
            }

            if (stderr.isNotBlank()) System.err.println(stderr)
            stdout
        }
    } catch (_: TimeoutCancellationException) {
        process.destroyForcibly()
        throw IllegalStateException("${command.joinToString()} timed out")
    } catch (e: Exception) {
        process.destroyForcibly()
        throw e
    }
}