package dev.sz.aid

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val expandedArgs = expandArgFiles(args)
    val exitCode = CommandLine(AidCommand()).execute(*expandedArgs)
    exitProcess(exitCode)
}
