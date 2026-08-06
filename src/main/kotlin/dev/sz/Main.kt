package dev.sz

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val exitCode = CommandLine(AidCommand()).execute(*args)
    exitProcess(exitCode)
}