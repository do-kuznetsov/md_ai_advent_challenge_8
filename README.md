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
