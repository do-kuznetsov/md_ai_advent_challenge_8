plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.sibgear.features.assistant.memory.data.jsonfile"

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:assistant-memory:domain"))
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
