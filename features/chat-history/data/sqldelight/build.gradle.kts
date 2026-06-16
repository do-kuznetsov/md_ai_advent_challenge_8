plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

group = "com.sibgear.features.chat.history"

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:chat-history:domain"))
            implementation(libs.kotlinx.serialization.json)
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
        create("ChatHistoryDatabase") {
            packageName.set("com.sibgear.deepseek.chat.history.data.sqldelight.internal.database")
        }
    }
}
