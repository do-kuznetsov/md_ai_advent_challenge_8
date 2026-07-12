import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

group = "com.sibgear.server"

kotlin {
    jvm {
        mainRun {
            mainClass.set("com.sibgear.server.MainKt")
        }
    }

    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "server-ui.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        jvmMain.dependencies {
            implementation(project(":rag:domain"))
            implementation(project(":rag:data"))
            implementation(compose.runtime)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.websockets)
            runtimeOnly(libs.slf4j.nop)
        }

        wasmJsMain.dependencies {
            implementation(compose.ui)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.compose.material.icons.extended)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.server.test.host)
            implementation(libs.ktor.client.mock)
        }
    }
}

val wasmUiDist = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")

tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn("wasmJsBrowserDistribution")
    from(wasmUiDist) {
        into("static")
    }
}

tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
    jvmArgs("-Dkotlin-logging.logStartupMessage=false")
}
