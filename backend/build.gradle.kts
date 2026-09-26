plugins {
    id("org.jetbrains.kotlin.jvm")
}

import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "shinigami"
version = "1.0.0"

// This root module is a standalone Kotlin/JVM backend artifact. No Android plugin/library is used.

val migratedSourceTree = tasks.register<Sync>("prepareMigratedSourceTree") {
    into(layout.buildDirectory.dir("generated/migratedSources"))

    from("sources/hatsune") {
        include(
            "AlqanimeProvider/src/main/kotlin/**/*.kt",
            "AnimasuProvider/src/main/kotlin/**/*.kt",
            "AnimeSailProvider/src/main/kotlin/**/*.kt",
            "AnimeinProvider/main/kotlin/**/*.kt",
            "AnimexinProvider/main/kotlin/**/*.kt",
            "AnoboyProvider/main/kotlin/**/*.kt",
            "KuramanimeProvider/main/kotlin/**/*.kt",
            "KuronimeProvider/main/kotlin/**/*.kt",
            "NimegamiProvider/main/kotlin/**/*.kt",
            "NontonAnimeIDProvider/main/kotlin/**/*.kt",
            "OtakudesuProvider/main/kotlin/**/*.kt",
            "SamehadakuProvider/main/kotlin/**/*.kt",
            "WinbuProvider/main/kotlin/**/*.kt"
        )
    }

    from("sources/oce/BaseProvider/src/main/kotlin/com/baseprovider/streamix") {
        exclude(
            "android/**",
            "StreamixFallbackPipeline.kt",
            "StreamixLinkCollector.kt",
            "StreamixMovieSeriesDetector.kt",
            "StreamixProviderEngine.kt",
            "StreamixProviderMapper.kt"
        )
    }
    from("sources/oce/BaseProvider/src/main/kotlin/com/baseprovider/log") {
        include(
            "FailureType.kt",
            "LogLevel.kt",
            "Logging.kt",
            "SupabaseBakedConfig.kt"
        )
    }
    from("sources/oce/BaseProvider/src/main/kotlin/com/baseprovider/config") {
        include("*.kt")
    }
    from("sources/oce/BaseProvider/src/main/kotlin/com/baseprovider/extractor") {
        exclude(
            "CloudStreamMasterLinkAdapter.kt",
            "ConfigDrivenExtractor.kt",
            "ExtractorRegistry.kt",
            "ExtractorFallback.kt"
        )
    }
}

val migratedResourceTree = tasks.register<Sync>("prepareMigratedResources") {
    into(layout.buildDirectory.dir("generated/migratedResources"))
    from("sources/oce/BaseProvider/src/main/kotlin/com/baseprovider/config/extractors") {
        include("*.json")
    }
}

kotlin {
    sourceSets {
        getByName("main") {
            kotlin.srcDir(layout.buildDirectory.dir("generated/migratedSources"))
            resources.srcDir(layout.buildDirectory.dir("generated/migratedResources"))
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(migratedSourceTree)
    dependsOn(migratedResourceTree)
}

tasks.named("processResources") {
    dependsOn(migratedResourceTree)
}

tasks.test {
    useJUnitPlatform()
    exclude("**/streamix/e2e/**")
}

val liveE2eTest = tasks.register<Test>("liveE2eTest") {
    description = "Runs live JVM provider pool E2E against the configured public providers."
    group = "verification"
    useJUnitPlatform()
    include("**/streamix/e2e/**")
    shouldRunAfter(tasks.test)
}

dependencies {
    implementation(project(":backend:streamix-core"))
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.mozilla:rhino:1.8.1")
    implementation("org.json:json:20260814")
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}