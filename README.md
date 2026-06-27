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

Если агенту нужны и посещения, и погода, добавьте в настройках приложения два MCP сервера:
- visitors: `http://127.0.0.1:3000/mcp`
- weather: `http://127.0.0.1:3001/mcp`

В другом терминале можно запустить CLI MCP-клиент, передав URL нужного сервера:
```bash
./gradlew -q :mcp:client:jvmRun --args='http://127.0.0.1:3000/mcp'
```
