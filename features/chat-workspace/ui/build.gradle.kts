plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

group = "com.sibgear.features.chat.workspace"

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:chat:domain"))
            implementation(project(":features:chat:ui"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.compose.material.icons.core)
        }

        commonTest.dependencies {
            implementation(project(":features:chat:domain"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
