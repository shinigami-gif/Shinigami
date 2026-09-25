plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.serialization)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("shinigami.backend.MainKt")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.call.logging)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
