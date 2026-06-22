plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.sibgear.mcp.client"

kotlin {
    jvm {
        mainRun {
            mainClass.set("com.sibgear.mcp.client.MainKt")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.cio)
            implementation(libs.mcp.kotlin.client)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
