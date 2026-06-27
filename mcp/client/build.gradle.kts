plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

import org.gradle.api.tasks.JavaExec

group = "com.sibgear.mcp.client"

kotlin {
    jvm {
        mainRun {
            mainClass.set("com.sibgear.mcp.client.MainKt")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:chat:domain"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.cio)
            implementation(libs.mcp.kotlin.client)
            runtimeOnly(libs.slf4j.nop)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    standardInput = System.`in`
    jvmArgs("-Dkotlin-logging.logStartupMessage=false")
}
