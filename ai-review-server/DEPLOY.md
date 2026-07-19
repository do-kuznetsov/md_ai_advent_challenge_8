# AI Review VPS deploy

Сервер деплоится рядом с текущим demo `server`, но отдельным systemd service.

## Current deployment

- Host: `<host-placeholder>`
- Runtime user: `md-ai-demo`
- Deploy root: `/opt/md-ai-demo/current`
- Service: `md-ai-review.service`
- Public webhook URL for GitHub:

```text
http://<host-placeholder>/github/webhook
```

- Local health:

```bash
curl http://127.0.0.1:19090/health
```

`localhost.run` tunnel was tested, but it did not provide a public hostname in
the service logs. The current working route uses existing nginx on port `80`
and proxies only `/github/webhook` to `127.0.0.1:19090`.

## GitHub webhook setup

В GitHub настройка выполняется в репозитории, указанном в `.keys.txt` как:

```text
github_allowed_repo=OWNER/REPO
```

Откройте:

```text
Repository -> Settings -> Webhooks -> Add webhook
```

Заполните webhook:

```text
Payload URL: http://<host-placeholder>/github/webhook
Content type: application/json
Secret: значение github_webhook_secret из .keys.txt
SSL verification: недоступно для HTTP URL
```

В блоке events выберите:

```text
Let me select individual events
Pull requests
Active: enabled
```

После сохранения GitHub отправит `ping`. Успешная проверка:

```text
Response: 200 OK
Body: pong
```

Дальше создайте тестовый Pull Request или сделайте push в уже открытый PR.
Сервер обрабатывает события `pull_request.opened` и
`pull_request.synchronize`, запускает review в background job и публикует
Pull Request Review в GitHub.

Для диагностики на VPS:

```bash
systemctl status md-ai-review --no-pager
journalctl -u md-ai-review -n 200 --no-pager
tail -n 100 /var/log/nginx/access.log
tail -n 100 /var/log/nginx/error.log
```

## Build

```bash
./gradlew :ai-review-server:jvmTest :ai-review-server:jvmJar
```

Артефакты:

```text
ai-review-server/build/libs/ai-review-server-jvm-1.0.0.jar
```

Для запуска нужен jar и runtime classpath dependencies.

## Runtime

Рекомендуемые env для service:

```text
AI_REVIEW_HOST=127.0.0.1
AI_REVIEW_PORT=19090
RAG_INDEX_DIR=<deploy-root>/current/rag/indexed
EMBEDDING_MODEL_DIR=<deploy-root>/current/rag/models/nomic-embed-text
```

Секреты по плану зашиваются в jar на этапе сборки из `.keys.txt`.

Entrypoint:

```bash
java -cp '<deploy-root>/current/app/ai-review-server.jar:<deploy-root>/current/lib/*' com.sibgear.aireview.MainKt
```

## systemd

Пример unit:

```ini
[Unit]
Description=MD AI Review Server
After=network.target

[Service]
User=<app-user>
WorkingDirectory=<deploy-root>/current
Environment=AI_REVIEW_HOST=127.0.0.1
Environment=AI_REVIEW_PORT=19090
Environment=RAG_INDEX_DIR=<deploy-root>/current/rag/indexed
Environment=EMBEDDING_MODEL_DIR=<deploy-root>/current/rag/models/nomic-embed-text
ExecStart=/usr/bin/java -cp <deploy-root>/current/app/ai-review-server.jar:<deploy-root>/current/lib/* com.sibgear.aireview.MainKt
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

## Tunnel

Для учебного запуска можно поднять отдельный tunnel service поверх `localhost.run`:

```bash
ssh -R 80:127.0.0.1:19090 nokey@localhost.run
```

URL из вывода tunnel используйте как GitHub webhook Payload URL:

```text
https://<tunnel-host>/github/webhook
```

## Smoke checks

```bash
curl http://127.0.0.1:19090/health
journalctl -u md-ai-review -n 100 --no-pager
```

GitHub должен успешно отправить `ping`, а при создании тестового PR должен появиться Pull Request Review с marker `<!-- ai-review:... -->`.
