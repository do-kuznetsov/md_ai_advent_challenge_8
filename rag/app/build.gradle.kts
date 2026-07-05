plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

import org.gradle.api.tasks.JavaExec

group = "com.sibgear.rag.app"

kotlin {
    jvm {
        mainRun {
            mainClass.set("com.sibgear.rag.app.MainKt")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":rag:domain"))
            implementation(project(":rag:data"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.cio)
            runtimeOnly(libs.slf4j.nop)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
    standardInput = System.`in`
    jvmArgs("-Dkotlin-logging.logStartupMessage=false")
}
