pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "md-ai-advent-challenge-8"

include(
    ":app",
    ":features:chat:domain",
    ":features:chat:data:deepseek",
    ":features:chat:data:openrouter",
    ":features:chat:ui",
    ":features:chat-history:domain",
    ":features:chat-history:data",
    ":features:chat-workspace:ui",
)
