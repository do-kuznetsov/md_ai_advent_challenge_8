# Day 22 RAG Control Questions

## RAG vs No-RAG Questions

| # | question | expected answer | expected sources | without RAG notes | with RAG notes |
|---:|---|---|---|---|---|
| 1 | Какие основные идеи лежат в архитектурных принципах feature-module? | Нужно упомянуть смесь DDD и Clean, разделение data/domain/ui и назначение domain как центра правил. | `intro/architectural_principles.md`, sections `Архитектурные принципы`, `Модуль :domain` | Модель может ответить общими словами про Clean Architecture. | Должен появиться конкретный контекст DDD + Clean и роли модулей. |
| 2 | Что такое composite builds в проекте? | Это крупные наборы модулей, объединенные направлением, используемые для разделения проекта. | `intro/for_ios/gradle.md`, section `Composite Builds` | Модель может дать только определение Gradle composite build. | Ответ должен описать локальный смысл в проекте. |
| 3 | Какие плагины используются для настройки KMP модулей? | Нужно назвать `libs.plugins.omni.kmp.general` и `libs.plugins.omni.kmp.mobile`. | `intro.md`, section `Настройка KMP модулей` | Модель может назвать стандартные kotlin multiplatform плагины. | Ответ должен использовать конкретные omni-плагины из базы. |
| 4 | Что такое umbrella framework в контексте поставки KMP на iOS? | Это XCFramework для поставки нескольких независимых KMP-проектов как одного umbrella framework. | `umbrella/welcome.md` | Модель может дать общий Apple framework answer. | Ответ должен связать umbrella с несколькими KMP-проектами и XCFramework. |
| 5 | Какие фазы есть в руководстве по миграции Android модуля на KMP? | Нужно назвать подготовку/конфигурацию/компиляцию и acceptance/checklist шаги миграции. | `migrate/README.md`, `migrate/configure_phase.md`, `migrate/compile_phase.md`, `migrate/acceptance_checklist.md` | Модель может придумать произвольный migration flow. | Ответ должен опираться на конкретные migrate docs из индекса. |
| 6 | Что нужно проверить перед началом портирования Android модуля на KMP? | Нужно упомянуть checklist готовности: выполненный MPU 1.0, соответствие целевой схеме и подготовку feature-module к миграции. | `migrate/before_migration_checklist.md`, section `Checklist готовности Android модуля к портированию на KMP` | Модель может дать общий migration checklist без локальных требований. | Ответ должен ссылаться на конкретный readiness checklist из базы. |
| 7 | Какие общие рекомендации указаны для миграции feature-module на KMP? | Нужно упомянуть максимум кода в `commonMain`, аккуратную работу с исключениями и отказ от platform-specific API без необходимости. | `migrate/README.md`, section `Общие рекомендации` | Модель может перечислить типовые советы по KMP. | Ответ должен использовать рекомендации именно из migrate README. |
| 8 | Чем отличаются `implementation` и `api` при подключении Gradle зависимостей? | Нужно объяснить, что `implementation` рекомендуется и скрывает зависимость от потребителей, а `api` делает ее доступной наружу. | `intro/for_ios/gradle.md`, section `Подключение зависимостей` | Модель может ответить общим Gradle знанием. | Ответ должен совпасть с локальным описанием подключения зависимостей. |
| 9 | Как включаются iOS цели при миграции feature-module? | Нужно сказать, что iOS цели по умолчанию выключены и их нужно активировать в composite build портируемого feature-module. | `migrate/activate_ios_target.md`, section `Включить iOS цели` | Модель может дать общий Kotlin target setup. | Ответ должен описать локальное правило про выключенные по умолчанию iOS targets. |
| 10 | Что такое `kmp-stdlib` и как его подключать? | Это набор KMP-модулей с базовыми переиспользуемыми решениями; подключается в `commonMain.dependencies` через `implementation(tanderLibs.stdlib.*)`. | `migrate/compile_feature-module.md`, section `kmp-stdlib` | Модель может придумать внешний stdlib или Kotlin stdlib. | Ответ должен описать внутренний `kmp-stdlib` из документации. |

## Chunking Strategy Comparison Questions

Эти вопросы лучше всего показывают разницу между fixed и structure chunking: они завязаны на границы файлов, заголовков и узких секций. Сравнивайте не только итоговый ответ, но и найденные `source`, `section`, `chunk_id`.

| # | question | expected answer | expected sources | why it shows fixed vs structure difference |
|---:|---|---|---|---|
| C1 | Какие Gradle-модули обычно входят в `feature-module`, и за что отвечает каждый? | Нужно перечислить типичные модули `:data`, `:domain`, `:presentation:logic`, `:presentation:ui` и кратко описать ответственность каждого. | `intro/architectural_principles.md`, sections `Структура feature-module`, `Описание типичных Gradle модулей внутри feature-module` | Structure должен лучше сохранить границы архитектурных секций; fixed может смешать соседние описания модулей. |
| C2 | Чем `flow-module` отличается от обычного `feature-module`? | Нужно объяснить, что `flow-module` интегрирует несколько `feature-module` и реализует пользовательский сценарий/flow. | `intro/architectural_principles.md`, section `flow-module - интеграция нескольких feature-module` | Это короткая точечная секция внутри большого документа; structure должен точнее выбрать ее целиком. |
| C3 | Что нельзя переносить в `presentation:logic` при разделении UI-модуля? | Нужно назвать ограничения из миграционного гайда и не смешивать их с Compose UI-частью. | `migrate/split_ui_module.md`, section `Что нельзя переносить в presentation:logic` | Structure должен лучше отделить запреты для `logic` от соседних шагов split/migration. |
| C4 | Какие шаги входят в фазу конфигурации миграции Android модуля на KMP? | Нужно описать действия именно из фазы конфигурации, без подмены фазой компиляции или acceptance checklist. | `migrate/configure_phase.md`, section `Фаза конфигурации` | Вопрос проверяет попадание в конкретный документ и заголовок; fixed чаще цепляет соседний migration-контекст. |
| C5 | Какие типичные ошибки компиляции описаны при сборке feature-module под целевые платформы? | Нужно перечислить ошибки из compile-гайда и способы устранения, если они есть в найденных чанках. | `migrate/compile_feature-module.md`, section `Типичные ошибки компиляции и способы их устранения` | Structure должен вернуть секцию с troubleshooting, а fixed может смешать общую фазу компиляции и команды сборки. |
| C6 | Какие изменения в `plugins` нужны для KMP-конфигурации non-presentation модулей? | Нужно назвать замену Android/plugin-conventions на `libs.plugins.omni.kmp.mobile` для `data` и `libs.plugins.omni.kmp.general` для остальных модулей. | `migrate/configure_non_presentation_modules.md`, section `Использование плагинов для KMP` | Это узкая секция внутри configure-гайда; structure должен точнее вернуть plugin-specific блок. |
| C7 | Что нужно перенести из секции `android` при конфигурации KMP модуля? | Нужно упомянуть перенос `namespace` внутрь секции `kotlin` и возможные другие свойства вроде `consumerProguardFiles` или `buildFeatures`. | `migrate/configure_non_presentation_modules.md`, section `Секция android` | Вопрос завязан на короткий заголовок `Секция android`; fixed может смешать с зависимостями и plugins. |
| C8 | Какие Dagger элементы нужно удалить из `presentation:logic`? | Нужно перечислить Dagger-аннотации, зависимости `tanderStack.dagger.*`, Dagger modules/components/subcomponents и generated wiring. | `migrate/split_ui_module.md`, section `Удаление Dagger из presentation:logic` | Structure должен достать отдельную секцию удаления Dagger, а не общий список того, что нельзя переносить. |
| C9 | Какие типичные причины ошибок Gradle sync и способы исправления описаны в гайде? | Нужно назвать примеры: `omniProject(":util")`, замена на `tanderLibs.stdlib.either`, старый `network` и явные зависимости. | `migrate/sync_project.md`, section `Типичные причины ошибок и способы их устранения` | Это troubleshooting-секция рядом с командами sync и транзитивными зависимостями; важно не смешать соседние блоки. |
| C10 | Что нужно сделать для публикации новой версии umbrella framework через CI? | Нужно описать локальные изменения в umbrella/подключаемых модулях, push в новую ветку, MR и CI-процесс публикации. | `umbrella/publish.md`, sections `Публикация новой версии`, `CI` | Structure должен отделить CI-инструкцию от короткой секции `Локально`, где публикация недоступна без кредов. |

## No-Answer / Anti-Hallucination Questions

Эти вопросы проверяют режим "не знаю": релевантного ответа в документации нет. Первый вопрос намеренно полностью вне темы, остальные близки к IT/KMP/мобильной разработке, но не покрыты индексированной базой.

| # | question | expected behavior | why answer is absent |
|---:|---|---|---|
| N1 | Как приготовить борщ в KMP feature-module? | Ассистент должен сказать "не знаю" и попросить уточнение. | Вопрос бытовой и не относится к документации KMP. |
| N2 | Как настроить SwiftUI preview для экранов, использующих KMP-модуль? | Ассистент должен сказать "не знаю" или явно указать, что в контексте нет инструкции. | В документации есть интеграция KMP с iOS, но нет инструкции по SwiftUI preview. |
| N3 | Как настроить Crashlytics для ошибок из Kotlin/Native в этом проекте? | Ассистент должен сказать "не знаю" или попросить уточнить источник. | В базе есть iOS/KMP и umbrella, но нет Crashlytics setup. |
| N4 | Какие правила ProGuard/R8 нужны для KMP feature-module после миграции? | Ассистент должен сказать "не знаю" или не давать конкретные правила без источника. | В документации упоминаются Gradle/Android настройки, но нет готовых ProGuard/R8 правил. |
| N5 | Как подключить SQLDelight migrations к `commonMain` в этом проекте? | Ассистент должен сказать "не знаю" или попросить релевантную документацию. | В базе есть общие KMP рекомендации, но нет SQLDelight migration guide. |

## Day 25 Long Session Scenario

Сценарий проверяет, что чат удерживает цель, уточнения, ограничения и термины на протяжении длинной сессии, а RAG-ответы продолжают возвращать источники и цитаты.

| # | role | message | expected task memory | expected RAG/source behavior |
|---:|---|---|---|---|
| 1 | user | Цель: мигрировать feature-module `promocodes-list` на KMP. | `goal` = мигрировать `promocodes-list` на KMP. | Retrieval должен учитывать migration docs. |
| 2 | assistant | Описывает общий план миграции и просит уточнить структуру модулей. | Память не меняется. | Ответ с источниками из `migrate/README.md`, `migrate/before_migration_checklist.md`. |
| 3 | user | У нас модуль состоит из `data`, `domain`, `presentation:logic` и `presentation:ui`. | Добавляется clarified fact о структуре модулей. | Следующий поиск должен учитывать все названия модулей. |
| 4 | assistant | Объясняет, какие части можно готовить к KMP и где нужна осторожность. | Память не меняется. | Источники: `intro/architectural_principles.md`, `migrate/split_ui_module.md`. |
| 5 | user | Ограничение: сначала нельзя трогать `presentation:ui`, работаем только с non-presentation модулями. | Добавляется constraint про `presentation:ui` и non-presentation scope. | Retrieval должен смещаться к configure non-presentation docs. |
| 6 | assistant | Отвечает про настройку non-presentation модулей с учетом ограничения. | Память не меняется. | Источники: `migrate/configure_non_presentation_modules.md`. |
| 7 | user | Термин: `logic` = `presentation:logic`. | Добавляется term `logic = presentation:logic`. | Follow-up с `logic` должен резолвиться как `presentation:logic`. |
| 8 | assistant | Подтверждает термин и объясняет роль logic-модуля. | Память не меняется. | Источники: `intro/architectural_principles.md`, `migrate/split_ui_module.md`. |
| 9 | user | А какие plugins нужны для `data` и `domain` в нашем случае? | Используются goal + module fact + constraint. | Источники: `migrate/configure_non_presentation_modules.md`, section `Использование плагинов для KMP`. |
| 10 | assistant | Называет `libs.plugins.omni.kmp.mobile` для `data` и `libs.plugins.omni.kmp.general` для остальных. | Память не меняется. | Обязательны источники и цитаты из plugin-секции. |
| 11 | user | После sync падает `omniProject(":util")` и импорты Java SDK. Что проверять? | Добавляется clarified fact про ошибки sync/compile. | Retrieval должен найти sync troubleshooting и compile troubleshooting. |
| 12 | assistant | Разделяет Gradle sync проблему и compile проблему, дает действия. | Память не меняется. | Источники: `migrate/sync_project.md`, `migrate/compile_feature-module.md`. |
| 13 | user | Собери итоговый checklist с учетом цели, ограничения и термина `logic`. | Используются все накопленные goal/facts/constraints/terms. | Retrieval должен захватить acceptance/checklist + configure/compile docs. |
| 14 | assistant | Дает checklist миграции `promocodes-list` без шагов для `presentation:ui` на первом этапе. | Память не меняется. | Источники: `migrate/acceptance_checklist.md`, `migrate/configure_phase.md`, `migrate/compile_phase.md`. |
| 15 | user | Какие источники ты использовал и что осталось неясным по нашей задаче? | Используются все накопленные элементы памяти. | Ответ должен перечислить источники и явно назвать неизвестные/неуточненные пункты без галлюцинаций. |
