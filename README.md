Собрать .jar для desktop:
```bash
./gradlew :app:packageUberJarForCurrentOS
```

## MCP-серверы

Структура MCP-серверов:
```text
mcp
  server
    visitors  # посещения, отчеты и SQLite-хранилище
    weather   # текущая погода через Open-Meteo
    worldtime # текущее локальное время города через Open-Meteo Geocoding
```

Запустить visitors MCP-сервер:
```bash
./gradlew :mcp:server:visitors:jvmRun
```

По умолчанию он доступен по адресу:
```text
http://127.0.0.1:3000/mcp
```

Доступные tools:
- `add_visit`
- `get_reports`
- `schedule_visit_report`

Visitors-сервер хранит SQLite-БД `visitor-log.db` рядом с jar/runtime-директорией. Путь к БД можно передать вторым аргументом, а `:memory:` включает in-memory режим.

Запустить weather MCP-сервер:
```bash
./gradlew :mcp:server:weather:jvmRun
```

По умолчанию он доступен по адресу:
```text
http://127.0.0.1:3001/mcp
```

Доступные tools:
- `get_weather`

Запустить worldtime MCP-сервер:
```bash
./gradlew :mcp:server:worldtime:jvmRun
```

По умолчанию он доступен по адресу:
```text
http://127.0.0.1:3002/mcp
```

Доступные tools:
- `get_world_time`

Worldtime-сервер принимает город в русском или английском написании, определяет timezone через Open-Meteo Geocoding и считает текущее локальное время на стороне JVM. API key не нужен.

Если агенту нужны посещения, погода и текущее время, добавьте в настройках приложения нужные MCP серверы:
- visitors: `http://127.0.0.1:3000/mcp`
- weather: `http://127.0.0.1:3001/mcp`
- worldtime: `http://127.0.0.1:3002/mcp`

В другом терминале можно запустить CLI MCP-клиент, передав URL нужного сервера:
```bash
./gradlew -q :mcp:client:jvmRun --args='http://127.0.0.1:3000/mcp'
```

## RAG CLI

CLI для подготовки локального RAG-индекса находится в модуле `:rag:app`.
Он читает текстовые документы и код из директории, режет их на чанки, получает embeddings через локальный Ollama и сохраняет результат в SQLite.

Перед запуском:
- установите и запустите Ollama;
- скачайте embedding-модель:

```bash
ollama pull nomic-embed-text
```

Пример запуска:

```bash
./gradlew -q :rag:app:jvmRun --args='--input /absolute/input_path/ --output /absolute/output_path/ --strategy both --chunk-size 500 --overlap 50 --model nomic-embed-text --ollama-url http://localhost:11434'
```

Аргументы:
- `--input` - директория с документами для индексации, обязательный аргумент;
- `--output` - директория для SQLite-БД, обязательный аргумент;
- `--strategy` - стратегия chunking: `fixed`, `structure` или `both`, по умолчанию `both`;
- `--chunk-size` - размер чанка в токенах/словах, по умолчанию `500`;
- `--overlap` - перекрытие между соседними чанками, по умолчанию `50`;
- `--model` - Ollama embedding-модель, по умолчанию `nomic-embed-text`;
- `--ollama-url` - адрес локального Ollama, по умолчанию `http://localhost:11434`.

Для `--strategy both` CLI создаёт две БД:
- `rag-fixed.sqlite` - индекс с чанками фиксированного размера;
- `rag-structure.sqlite` - индекс с чанками по структуре документов.

После запуска CLI печатает сравнение стратегий:

```text
strategy | files | chunks | embedding_dimension | elapsed_ms | db_size_bytes
```

Проверить результат можно так:

```bash
ls -lh /absolute/path/to/rag/indexed
sqlite3 /absolute/path/to/rag/indexed/rag-fixed.sqlite 'select count(*) from chunks;'
sqlite3 /absolute/path/to/rag/indexed/rag-structure.sqlite 'select count(*) from chunks;'
```

Лучше передавать `--output` абсолютным путём: Gradle `jvmRun` запускает процесс из директории модуля `rag/app`, поэтому относительный путь будет считаться относительно неё.

PDF сейчас не индексируются: CLI обрабатывает текстовые файлы, markdown и код, а PDF пропускает с предупреждением.
