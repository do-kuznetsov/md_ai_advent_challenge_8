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
