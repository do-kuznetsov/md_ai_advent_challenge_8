Собрать .jar для desktop:
```bash
./gradlew :app:packageUberJarForCurrentOS
```

Запустить тестовый MCP-сервер с tool `ololo`:
```bash
./gradlew :mcp:server:jvmRun
```

В другом терминале запустить CLI MCP-клиент:
```bash
./gradlew :mcp:client:jvmRun
```

По умолчанию сервер слушает `http://127.0.0.1:3000/mcp`, клиент подключается туда же и выводит список доступных tools. В выводе клиента должен появиться `ololo`.
