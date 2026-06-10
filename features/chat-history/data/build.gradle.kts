plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "com.sibgear.features.chat.history"

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:chat-history:domain"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
