import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
// import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.buildkonfig)
}

group = "com.sibgear"
version = "1.0.0"

val deepSeekApiKey = readRequiredKey("deepseek_api_key")
val openRouterAiKey = readRequiredKey("openrouter_ai_key")

// @OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvm("desktop")

//    wasmJs {
//        outputModuleName.set("deepseek-client")
//        browser {
//            commonWebpackConfig {
//                outputFileName = "deepseek-client.js"
//            }
//        }
//        binaries.executable()
//    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.cio)
        }

//        val wasmJsMain by getting
//        wasmJsMain.dependencies {
//            implementation(libs.ktor.client.js)
//        }
    }
}

buildkonfig {
    packageName = "com.sibgear.deepseek.config"
    objectName = "BuildConfig"

    defaultConfigs {
        buildConfigField(STRING, "DEEPSEEK_API_KEY", deepSeekApiKey, const = true)
        buildConfigField(STRING, "OPENROUTER_AI_KEY", openRouterAiKey, const = true)
    }
}

compose.desktop {
    application {
        mainClass = "com.sibgear.deepseek.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "DeepSeek Client"
            packageVersion = "1.0.0"
        }
    }
}

fun readRequiredKey(keyName: String): String {
    val keysFile = file(".keys.txt")
    if (!keysFile.exists()) {
        throw GradleException(
            "Missing .keys.txt. Create it in the project root with: $keyName=<value>",
        )
    }

    val properties = Properties()
    keysFile.inputStream().use(properties::load)

    val value = properties.getProperty(keyName)?.trim()
    if (value.isNullOrEmpty()) {
        throw GradleException(
            "Missing $keyName in .keys.txt. Expected format: $keyName=<value>",
        )
    }

    return value
}
