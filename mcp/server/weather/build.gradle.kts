plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.sibgear.mcp.server"

kotlin {
    jvm {
        mainRun {
            mainClass.set("com.sibgear.mcp.server.weather.MainKt")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.mcp.kotlin.server)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.mock)
            implementation(libs.mcp.kotlin.client)
        }
    }
}
