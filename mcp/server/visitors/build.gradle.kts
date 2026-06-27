plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.sibgear.mcp.server"

kotlin {
    jvm {
        mainRun {
            mainClass.set("com.sibgear.mcp.server.visitors.MainKt")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.server.cio)
            implementation(libs.mcp.kotlin.server)
            implementation(libs.sqldelight.sqlite.driver)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.cio)
            implementation(libs.mcp.kotlin.client)
        }
    }
}
