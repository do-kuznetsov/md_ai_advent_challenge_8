# VPS deploy runbook

Production-like demo is deployed to a VPS running Ubuntu 24.04 LTS.
Public access is plain HTTP on port `80`; Ktor and `llama-server` listen only
on loopback.

## Current deployment

- URL: `http://<server-host>/`
- SSH user for maintenance: `<ssh-user>`
- Runtime user: `<app-user>`
- Deploy root: `<deploy-root>`
- Active release: `<deploy-root>/current`
- Installed release: `<deploy-root>/releases/<release-id>`
- App entrypoint:
  ```bash
  java -cp '<deploy-root>/current/app/server.jar:<deploy-root>/current/lib/*' com.sibgear.server.MainKt
  ```
- `llama.cpp`: `/opt/llama.cpp/build/bin/llama-server`
- Pinned `llama.cpp` ref: `b9982`
- Pinned `llama.cpp` commit: `99f3dc32296f825fec94f202da1e9fede1e78cf9`
- Model alias: `qwen3-1.7b`
- Context size: `16384`

## Files on VPS

```text
<deploy-root>/current/app/server.jar
<deploy-root>/current/lib/*.jar
<deploy-root>/current/models/Qwen3/Qwen3-1.7B-Q4_K_M.gguf
<deploy-root>/current/rag/models/nomic-embed-text/model.onnx
<deploy-root>/current/rag/models/nomic-embed-text/tokenizer.json
<deploy-root>/current/rag/models/bge-reranker-v2-m3/model.onnx
<deploy-root>/current/rag/models/bge-reranker-v2-m3/tokenizer.json
<deploy-root>/current/rag/indexed/rag-fixed.sqlite
<deploy-root>/current/rag/indexed/rag-structure.sqlite
```

## Services

```bash
systemctl status md-ai-llama md-ai-server nginx --no-pager
systemctl restart md-ai-llama md-ai-server nginx
journalctl -u md-ai-llama -n 100 --no-pager
journalctl -u md-ai-server -n 100 --no-pager
```

`md-ai-server.service` depends on `md-ai-llama.service` and waits for
`http://127.0.0.1:8081/health` before starting Ktor.

## Local build and bundle

Build and test locally:

```bash
./gradlew :server:jvmTest :server:jvmJar
```

The `:server:jvmJar` task includes the Wasm UI in jar resources under `static/`.
For deployment, package:

- `server/build/libs/server-jvm-*.jar` as `app/server.jar`
- resolved `jvmRuntimeClasspath` jars as `lib/*.jar`
- GGUF model, ONNX models, tokenizers, and RAG SQLite indexes

Exclude macOS metadata such as `.DS_Store` and `._*`; AppleDouble files in
`lib/` can break the Java classpath.

## VPS package policy

Install everything available from `apt`. Build from source only when a required
component is absent from Ubuntu packages or incompatible.

Required packages:

```bash
apt-get update
apt-get install -y \
  openjdk-21-jre-headless nginx curl unzip rsync git \
  build-essential cmake pkg-config ca-certificates libgomp1 \
  libopenblas-dev ninja-build ccache
```

There was no compatible `llama-server` package in Ubuntu apt at deployment time,
so `llama.cpp` was built from the pinned upstream ref.

## llama.cpp build

```bash
git clone https://github.com/ggml-org/llama.cpp.git /opt/llama.cpp
git -C /opt/llama.cpp checkout b9982
cmake -S /opt/llama.cpp -B /opt/llama.cpp/build \
  -DCMAKE_BUILD_TYPE=Release \
  -DGGML_NATIVE=ON \
  -DGGML_BLAS=ON \
  -DGGML_BLAS_VENDOR=OpenBLAS
cmake --build /opt/llama.cpp/build --config Release -j "$(nproc)"
```

Write the chosen ref and commit to `/opt/llama.cpp/VERSION`.

## Runtime environment

Ktor service environment:

```text
LLAMA_BASE_URL=http://127.0.0.1:8081
LLAMA_MODEL_ID=qwen3-1.7b
LLAMA_CONTEXT_SIZE=16384
RAG_INDEX_DIR=<deploy-root>/current/rag/indexed
EMBEDDING_MODEL_DIR=<deploy-root>/current/rag/models/nomic-embed-text
RERANKER_MODEL_DIR=<deploy-root>/current/rag/models/bge-reranker-v2-m3
SERVER_HOST=127.0.0.1
SERVER_PORT=18080
OPENBLAS_NUM_THREADS=4
```

`llama-server` command:

```bash
/opt/llama.cpp/build/bin/llama-server \
  --model <deploy-root>/current/models/Qwen3/Qwen3-1.7B-Q4_K_M.gguf \
  --host 127.0.0.1 \
  --port 8081 \
  --ctx-size 16384 \
  --alias qwen3-1.7b \
  --threads 4 \
  --threads-batch 4
```

## Smoke checks

```bash
curl http://127.0.0.1:8081/health
curl http://127.0.0.1:8081/v1/models
curl http://127.0.0.1:18080/health
curl http://127.0.0.1:18080/api/config
curl -I http://<server-host>/
ss -ltnp | grep -E ':(22|80|8081|18080)\b'
```

Expected:

- `llama-server /health`: `{"status":"ok"}`
- `llama-server /v1/models`: contains `qwen3-1.7b`
- Ktor `/health`: `OK`
- Ktor `/api/config`: `contextSize` is `16384`
- Public `HEAD /`: `200 OK`
- Public listeners: `22` and `80`
- Loopback listeners: `127.0.0.1:8081` and loopback `18080`

Full post-deploy check should include one public WebSocket prompt to
`/api/chat` with RAG enabled and `maxTokens` around `64`.

## Swap and resources

The VPS has 8 GiB RAM class hardware. Deployment configured:

- existing zram swap: about 3.8 GiB
- `/swapfile`: 8 GiB
- `vm.swappiness=10`

Check with:

```bash
free -h
swapon --show
df -h / /opt
```

## Rollback

Releases are timestamped under `<deploy-root>/releases`.

Rollback to a previous release:

```bash
ln -sfn <deploy-root>/releases/<previous-release> <deploy-root>/current
chown -h <app-user>:<app-user> <deploy-root>/current
systemctl restart md-ai-server
```

Restart `md-ai-llama` too if the model path or `llama-server` settings changed.

## Notes

- Runtime services must not run as a privileged user.
- HTTPS/domain are not configured yet.
- UFW had pre-existing allow rules for `443` and `ispmanager`; they were not
  changed during this deployment.
- `curl -I /` is handled by Nginx because Ktor does not handle `HEAD /`.
