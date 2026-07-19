import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.sibgear.ai.review"

val generatedSecretsDir = layout.buildDirectory.dir("generated/ai-review-secrets/kotlin")

val generateAiReviewSecrets by tasks.registering {
    val keysFile = rootProject.layout.projectDirectory.file(".keys.txt")
    inputs.file(keysFile).optional()
    outputs.dir(generatedSecretsDir)

    doLast {
        val values = if (keysFile.asFile.isFile) {
            keysFile.asFile.readLines()
                .map(String::trim)
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val separator = line.indexOf('=').takeIf { it >= 0 } ?: line.indexOf(':').takeIf { it >= 0 }
                    separator?.let { line.take(it).trim() to line.drop(it + 1).trim() }
                }
                .toMap()
        } else {
            emptyMap()
        }
        val output = generatedSecretsDir.get().file("com/sibgear/aireview/generated/GeneratedAiReviewSecrets.kt").asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            package com.sibgear.aireview.generated

            internal object GeneratedAiReviewSecrets {
                const val DeepSeekApiKey: String = "${values["deepseek_api_key"].orEmpty().escapedKotlin()}"
                const val GitHubToken: String = "${values["github_token"].orEmpty().escapedKotlin()}"
                const val GitHubWebhookSecret: String = "${values["github_webhook_secret"].orEmpty().escapedKotlin()}"
                const val GitHubAllowedRepo: String = "${values["github_allowed_repo"].orEmpty().escapedKotlin()}"
            }
            """.trimIndent(),
        )
    }
}

kotlin {
    jvm {
        mainRun {
            mainClass.set("com.sibgear.aireview.MainKt")
        }
    }

    sourceSets {
        jvmMain {
            kotlin.srcDir(generatedSecretsDir)
            dependencies {
                implementation(project(":rag:domain"))
                implementation(project(":rag:data"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.content.negotiation)
                runtimeOnly(libs.slf4j.nop)
            }
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.server.test.host)
        }
    }
}

tasks.named("compileKotlinJvm") {
    dependsOn(generateAiReviewSecrets)
}

tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
    jvmArgs("-Dkotlin-logging.logStartupMessage=false")
}

private fun String.escapedKotlin(): String =
    replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
