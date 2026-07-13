package com.sibgear.server

data class ServerConfig(
    val host: String = env("SERVER_HOST") ?: "127.0.0.1",
    val port: Int = env("SERVER_PORT")?.toIntOrNull() ?: 8080,
    val llamaBaseUrl: String = env("LLAMA_BASE_URL") ?: "http://127.0.0.1:8081",
    val llamaModelId: String = env("LLAMA_MODEL_ID") ?: "qwen3-1.7b",
    val llamaContextSize: Int = env("LLAMA_CONTEXT_SIZE")?.toIntOrNull() ?: 32768,
    val ragIndexDirectory: String = env("RAG_INDEX_DIR") ?: "rag/indexed",
    val embeddingModelDirectory: String = env("EMBEDDING_MODEL_DIR") ?: "rag/models/nomic-embed-text",
    val rerankerModelDirectory: String = env("RERANKER_MODEL_DIR") ?: "rag/models/bge-reranker-v2-m3",
)

private fun env(name: String): String? =
    System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
