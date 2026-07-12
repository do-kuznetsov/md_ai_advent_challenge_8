# Server RAG + Local LLM Demo

Короткая инструкция для локального запуска demo-связки:

1. отдельно поднять `llama-server`;
2. отдельно запустить Kotlin/Ktor server с Compose Web UI.

## Что должно быть на машине

- Установлен `llama-server`.
- GGUF модель лежит здесь:

```text
generative/models/Qwen3/Qwen3-1.7B-Q4_K_M.gguf
```

- ONNX embedding-модель лежит здесь:

```text
rag/models/nomic-embed-text/model.onnx
rag/models/nomic-embed-text/tokenizer.json
```

- ONNX reranker лежит здесь:

```text
rag/models/bge-reranker-v2-m3/model.onnx
rag/models/bge-reranker-v2-m3/tokenizer.json
```

- RAG SQLite индексы лежат здесь:

```text
rag/indexed/rag-fixed.sqlite
rag/indexed/rag-structure.sqlite
```

## Запуск llama.cpp server

Из корня репозитория:

```bash
llama-server \
  --model generative/models/Qwen3/Qwen3-1.7B-Q4_K_M.gguf \
  --host 127.0.0.1 \
  --port 8081 \
  --ctx-size 32768 \
  --alias qwen3-1.7b
```

Проверка:

```bash
curl http://127.0.0.1:8081/health
curl http://127.0.0.1:8081/v1/models
```

Ожидаемо:

- `/health` возвращает `{"status":"ok"}`;
- в `/v1/models` есть модель `qwen3-1.7b`.

## Запуск Kotlin/Ktor server

Во втором терминале, из корня репозитория:

```bash
LLAMA_BASE_URL=http://127.0.0.1:8081 \
LLAMA_MODEL_ID=qwen3-1.7b \
LLAMA_CONTEXT_SIZE=32768 \
RAG_INDEX_DIR=rag/indexed \
EMBEDDING_MODEL_DIR=rag/models/nomic-embed-text \
RERANKER_MODEL_DIR=rag/models/bge-reranker-v2-m3 \
SERVER_HOST=127.0.0.1 \
SERVER_PORT=18080 \
./gradlew :server:jvmRun
```

После успешного старта Ktor server напечатает URL UI в консоль.

Проверка:

```bash
curl http://127.0.0.1:18080/health
curl http://127.0.0.1:18080/api/config
```

UI доступен здесь:

```text
http://127.0.0.1:18080
```
