package dev.sz.aid

import picocli.CommandLine

enum class OutputLanguage(val directiveResource: String?) {
    EN(null),
    RU("ru.md");

    class Converter : CommandLine.ITypeConverter<OutputLanguage> {
        override fun convert(value: String): OutputLanguage = valueOf(value.uppercase())
    }
}
