plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
}

group = "com.sibgear.features.assistant.memory.data.sqldelight"

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:assistant-memory:domain"))
            implementation(libs.sqldelight.sqlite.driver)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("AssistantMemoryDatabase") {
            packageName.set("com.sibgear.deepseek.assistant.memory.data.sqldelight.internal.database")
        }
    }
}
