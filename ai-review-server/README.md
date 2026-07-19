# AI Review Server

Ktor-сервер для автоматического AI review GitHub Pull Request.

## Секреты

Перед сборкой добавьте в корневой `.keys.txt`:

```text
deepseek_api_key=...
github_token=...
github_webhook_secret=...
github_allowed_repo=owner/repo
```

### Как получить значения для GitHub

`github_allowed_repo` - это полный slug репозитория из URL:

```text
https://github.com/OWNER/REPO
```

В `.keys.txt` он записывается так:

```text
github_allowed_repo=OWNER/REPO
```

`github_webhook_secret` - случайная секретная строка, которую нужно указать и в `.keys.txt`, и в настройках GitHub webhook. Сгенерировать можно локально:

```bash
openssl rand -hex 32
```

В `.keys.txt`:

```text
github_webhook_secret=<generated-secret>
```

`github_token` - Fine-grained Personal Access Token:

1. GitHub -> avatar -> Settings.
2. Developer settings.
3. Personal access tokens.
4. Fine-grained tokens.
5. Generate new token.
6. Token name: `AI Review Bot`.
7. Expiration: например 30 или 90 дней.
8. Resource owner: владелец нужного репозитория.
9. Repository access: `Only select repositories`.
10. Выберите target repository.
11. Repository permissions:
    - `Pull requests`: `Read and write`.
    - `Metadata`: GitHub добавит автоматически.
12. Generate token и сразу скопируйте значение.

В `.keys.txt`:

```text
github_token=<fine-grained-pat>
```

После изменения `.keys.txt` нужно пересобрать `:ai-review-server`, потому что секреты генерируются в Kotlin source на этапе сборки.

Gradle читает `.keys.txt` на этапе сборки и генерирует Kotlin source в `build/generated/...`.
В runtime `.keys.txt` не нужен: значения уже находятся в jar.

Для тестов и локальных override можно использовать env:

```text
DEEPSEEK_API_KEY
GITHUB_TOKEN
GITHUB_WEBHOOK_SECRET
GITHUB_ALLOWED_REPO
AI_REVIEW_HOST
AI_REVIEW_PORT
RAG_INDEX_DIR
EMBEDDING_MODEL_DIR
```

## Запуск

```bash
./gradlew :ai-review-server:jvmRun
```

По умолчанию сервер слушает:

```text
http://127.0.0.1:19090
```

Проверка:

```bash
curl http://127.0.0.1:19090/health
```

## GitHub webhook

Для первого end-to-end теста пробросьте локальный порт через tunnel, например `localhost.run`:

```bash
ssh -R 80:127.0.0.1:19090 nokey@localhost.run
```

В GitHub repository settings создайте webhook:

- Payload URL: `https://<tunnel-host>/github/webhook`
- Content type: `application/json`
- Secret: значение `github_webhook_secret`
- Events: `Pull requests`

Сервер обрабатывает только `opened` и `synchronize`, `ping` отвечает `pong`.

Для текущего VPS deploy через nginx используйте инструкцию из
[`DEPLOY.md`](DEPLOY.md): там указан рабочий Payload URL и шаги настройки
webhook в GitHub.

## Поведение review

Пайплайн:

1. Проверяет `X-Hub-Signature-256`.
2. Проверяет, что repo совпадает с `github_allowed_repo`.
3. Получает changed files, unified diff и существующие PR reviews.
4. Пропускает повторную обработку того же `headSha`, если найден marker.
5. Ищет RAG-контекст в `rag/indexed`.
6. Отправляет diff + changed files + RAG-контекст в `deepseek-v4-pro`.
7. Публикует Pull Request Review с `event=COMMENT`.

Лимиты первой версии:

- до 50 файлов;
- до 200 KB diff/context в prompt;
- до 20 inline comments;
- inline comments ставятся только на changed lines `side=RIGHT`.

Если GitHub отклоняет inline comments, сервер повторяет публикацию как body-only review и переносит точечные замечания в общий текст.
