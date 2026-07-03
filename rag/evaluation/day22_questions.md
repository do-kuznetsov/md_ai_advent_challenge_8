# Day 22 RAG Control Questions

## RAG vs No-RAG Questions

| # | question | expected answer | expected sources | without RAG notes | with RAG notes |
|---:|---|---|---|---|---|
| 1 | Какие основные идеи лежат в архитектурных принципах feature-module? | Нужно упомянуть смесь DDD и Clean, разделение data/domain/ui и назначение domain как центра правил. | `intro/architectural_principles.md`, sections `Архитектурные принципы`, `Модуль :domain` | Модель может ответить общими словами про Clean Architecture. | Должен появиться конкретный контекст DDD + Clean и роли модулей. |
| 2 | Что такое composite builds в проекте? | Это крупные наборы модулей, объединенные направлением, используемые для разделения проекта. | `intro/for_ios/gradle.md`, section `Composite Builds` | Модель может дать только определение Gradle composite build. | Ответ должен описать локальный смысл в проекте. |
| 3 | Какие плагины используются для настройки KMP модулей? | Нужно назвать `libs.plugins.omni.kmp.general` и `libs.plugins.omni.kmp.mobile`. | `intro.md`, section `Настройка KMP модулей` | Модель может назвать стандартные kotlin multiplatform плагины. | Ответ должен использовать конкретные omni-плагины из базы. |
| 4 | Что такое umbrella framework в контексте поставки KMP на iOS? | Это XCFramework для поставки нескольких независимых KMP-проектов как одного umbrella framework. | `umbrella/welcome.md` | Модель может дать общий Apple framework answer. | Ответ должен связать umbrella с несколькими KMP-проектами и XCFramework. |
| 5 | Какие фазы есть в руководстве по миграции Android модуля на KMP? | Нужно назвать подготовку/конфигурацию/компиляцию и acceptance/checklist шаги миграции. | `migrate/README.md`, `migrate/configure_phase.md`, `migrate/compile_phase.md`, `migrate/acceptance_checklist.md` | Модель может придумать произвольный migration flow. | Ответ должен опираться на конкретные migrate docs из индекса. |

## Chunking Strategy Comparison Questions

Эти вопросы лучше всего показывают разницу между fixed и structure chunking: они завязаны на границы файлов, заголовков и узких секций. Сравнивайте не только итоговый ответ, но и найденные `source`, `section`, `chunk_id`.

| # | question | expected answer | expected sources | why it shows fixed vs structure difference |
|---:|---|---|---|---|
| C1 | Какие Gradle-модули обычно входят в `feature-module`, и за что отвечает каждый? | Нужно перечислить типичные модули `:data`, `:domain`, `:presentation:logic`, `:presentation:ui` и кратко описать ответственность каждого. | `intro/architectural_principles.md`, sections `Структура feature-module`, `Описание типичных Gradle модулей внутри feature-module` | Structure должен лучше сохранить границы архитектурных секций; fixed может смешать соседние описания модулей. |
| C2 | Чем `flow-module` отличается от обычного `feature-module`? | Нужно объяснить, что `flow-module` интегрирует несколько `feature-module` и реализует пользовательский сценарий/flow. | `intro/architectural_principles.md`, section `flow-module - интеграция нескольких feature-module` | Это короткая точечная секция внутри большого документа; structure должен точнее выбрать ее целиком. |
| C3 | Что нельзя переносить в `presentation:logic` при разделении UI-модуля? | Нужно назвать ограничения из миграционного гайда и не смешивать их с Compose UI-частью. | `migrate/split_ui_module.md`, section `Что нельзя переносить в presentation:logic` | Structure должен лучше отделить запреты для `logic` от соседних шагов split/migration. |
| C4 | Какие шаги входят в фазу конфигурации миграции Android модуля на KMP? | Нужно описать действия именно из фазы конфигурации, без подмены фазой компиляции или acceptance checklist. | `migrate/configure_phase.md`, section `Фаза конфигурации` | Вопрос проверяет попадание в конкретный документ и заголовок; fixed чаще цепляет соседний migration-контекст. |
| C5 | Какие типичные ошибки компиляции описаны при сборке feature-module под целевые платформы? | Нужно перечислить ошибки из compile-гайда и способы устранения, если они есть в найденных чанках. | `migrate/compile_feature-module.md`, section `Типичные ошибки компиляции и способы их устранения` | Structure должен вернуть секцию с troubleshooting, а fixed может смешать общую фазу компиляции и команды сборки. |
