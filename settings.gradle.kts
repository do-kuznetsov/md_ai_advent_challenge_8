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
    ":features:chat:data",
    ":features:chat:ui",
    ":features:chat-workspace:ui",
)
