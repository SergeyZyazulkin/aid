plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    application
}

group = "dev.sz"
version = "1.7.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("info.picocli:picocli:4.7.7")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.kotest:kotest-assertions-core:6.2.2")
    testImplementation("com.squareup.okhttp3:mockwebserver3-junit5:5.4.0")
}

application {
    mainClass.set("dev.sz.aid.MainKt")
    applicationName = "aid"
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}