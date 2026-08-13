package dev.sz.aid

import picocli.CommandLine

enum class CodeScope {
    ALL,
    SOURCES,
    DIFF;

    class Converter : CommandLine.ITypeConverter<CodeScope> {
        override fun convert(value: String): CodeScope = valueOf(value.uppercase())
    }
}

