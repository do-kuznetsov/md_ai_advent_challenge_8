import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
}

group = "com.sibgear"
version = "1.0.0"

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvm("desktop")

    wasmJs {
        outputModuleName.set("deepseek-client")
        browser {
            commonWebpackConfig {
                outputFileName = "deepseek-client.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation("io.ktor:ktor-client-core:3.5.0")
            implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }

        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("io.ktor:ktor-client-cio:3.5.0")
        }

        val wasmJsMain by getting
        wasmJsMain.dependencies {
            implementation("io.ktor:ktor-client-js:3.5.0")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.sibgear.deepseek.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "DeepSeek Client"
            packageVersion = "1.0.0"
        }
    }
}
