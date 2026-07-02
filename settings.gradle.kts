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
    ":features:assistant-memory:domain",
    ":features:assistant-memory:data:json-file",
    ":features:assistant-memory:data:sqldelight",
    ":features:chat-history:domain",
    ":features:chat-history:data:json-file",
    ":features:chat-history:data:sqldelight",
    ":features:chat-workspace:ui",
    ":features:settings:ui",
    ":mcp:server:visitors",
    ":mcp:server:weather",
    ":mcp:server:worldtime",
    ":mcp:client",
    ":rag:domain",
    ":rag:data",
    ":rag:app",
)
