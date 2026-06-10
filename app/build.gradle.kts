import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:chat:domain"))
            implementation(project(":features:chat:data:deepseek"))
            implementation(project(":features:chat:data:openrouter"))
            implementation(project(":features:chat:ui"))
            implementation(project(":features:chat-history:data:json-file"))
            implementation(project(":features:chat-history:data:sqldelight"))
            implementation(project(":features:chat-history:domain"))
            implementation(project(":features:chat-workspace:ui"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
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

private val deepSeekApiKey = readRequiredKey("deepseek_api_key")
private val openRouterAiKey = readRequiredKey("openrouter_ai_key")

buildkonfig {
    packageName = "com.sibgear.deepseek.config"
    objectName = "BuildConfig"

    defaultConfigs {
        buildConfigField(STRING, "DEEPSEEK_API_KEY", deepSeekApiKey, const = true)
        buildConfigField(STRING, "OPENROUTER_AI_KEY", openRouterAiKey, const = true)
    }
}

private fun readRequiredKey(keyName: String): String {
    val keysFile = rootProject.file(".keys.txt")
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
