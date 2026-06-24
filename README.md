Собрать .jar для desktop:
```bash
./gradlew :app:packageUberJarForCurrentOS
```

Запустить тестовый MCP-сервер с tool `visitor_log`:
```bash
./gradlew :mcp:server:jvmRun
```

В другом терминале запустить CLI MCP-клиент, передав URL сервера:
```bash
./gradlew -q :mcp:client:jvmRun --args='http://127.0.0.1:3000/mcp'
```

Клиент выведет список доступных tools, попросит ввести название tool, затем последовательно запросит аргументы по схеме выбранного tool.

Пример ввода для `visitor_log`:
```text
visitor_log
Dmitry
2026-06-24 20:00
Novosibirsk
5
```

Пример ответа:
```text
Dmitry из Novosibirsk заходил в 2026-06-24 20:00 через visitor-log-cli-client/1.0.0
```
