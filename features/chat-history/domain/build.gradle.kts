plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "com.sibgear.features.chat.history"

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
