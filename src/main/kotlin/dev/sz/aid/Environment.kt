package dev.sz.aid

fun interface Environment {
    operator fun get(name: String): String?
}

object SystemEnvironment : Environment {
    override operator fun get(name: String): String? = System.getenv(name)
}