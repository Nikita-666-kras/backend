# Структура проектов GitLab для совместной работы Atris

**Цель:** все рабочие процессы команды ведутся в GitLab — код, задачи, ревью, деплой, документация, инфраструктура.  
**Корень:** группа `atris` на https://git.atris.site

---

## 1. Идея в одном взгляде

```text
atris/                          ← компания (Private)
│
├── _meta/                      ← процессы и шаблоны (не продукт)
│   ├── handbook                ← как работаем (wiki + md)
│   ├── templates               ← шаблоны репо / MR / Issue
│   └── roadmap                 ← эпики, приоритеты, релизы (Issues + Boards)
│
├── products/                   ← продуктовые репозитории
│   ├── blog-platform           ← микросервисы + UI (основной код)
│   ├── atris-site              ← сайт / лендинг / интеграции с Tilda
│   └── mobile-hub              ← концепт/будущее мобильного хаба (пока docs+design)
│
├── ops/                        ← эксплуатация
│   ├── infra                   ← nginx, docker, VPS, GitLab, DNS-заметки
│   ├── runbooks                ← инструкции «что делать если упало»
│   └── monitoring              ← чеклисты, алерты, скрипты (по мере роста)
│
├── work/                       ← рабочие пространства без «тяжёлого» кода
│   ├── support                 ← обращения клиентов → Issues
│   ├── content                 ← контент для блога/каталога (черновики, ТЗ)
│   └── partners                ← партнёрские материалы, КП, презентации
│
└── archive/                    ← старые проекты (read-only, Maintainer only)
```

**Правило:** один репозиторий = одна зона ответственности.  
Задачи живут в **Issues** того репо (или в `roadmap` для кросс-продуктовых).

---

## 2. Как процессы ложатся на GitLab

| Процесс | Где в GitLab | Как |
|---------|--------------|-----|
| Новая фича | `products/*` Issues → ветка → MR | label `feature` |
| Баг | Issue в продуктовом репо | label `bug`, priority |
| Ревью кода | Merge Request | `strict` / `free` по репо |
| Релиз | Milestone + tag | `v1.2.0`, Issue board «Release» |
| Деплой | CI/CD в продукте + `ops/infra` | pipeline / runbook |
| Документация | `handbook`, README, Wiki | MR в docs |
| Клиентский запрос | `work/support` Issue | → ссылка на Issue в продукте |
| Контент / Tilda | `work/content` | ТЗ → Issue в `blog-platform` |
| Инфра (nginx, GitLab) | `ops/infra` | MR + ваш approve (`strict`) |
| Планирование спринта | `roadmap` Board | колонки To Do / Doing / Done |
| Онбординг новичка | `handbook` | чеклист Issue |

---

## 3. Группы и репозитории подробно

### 3.1. `atris/_meta` — «как мы работаем»

#### `handbook` (режим: **free** или `strict` для критичных страниц)
Содержимое:
- `README.md` — с чего начать
- `workflow.md` — ветки, MR, кто мержит
- `roles.md` — Developer / Maintainer / Owner
- `environments.md` — staging / prod, URL
- `security.md` — секреты, `.env`, доступы
- Wiki группы — быстрые FAQ

Задачи: «обновить гайд по SSH», «добавить правило для hotfix».

#### `templates` (режим: **free**)
- `issue_templates/` — Bug, Feature, Support, Infra
- `merge_request_templates/` — стандартное описание MR
- `spring-service/` — каркас нового Java-сервиса
- `vue-app/` — каркас UI

Новый проект: **New project → Create from template**.

#### `roadmap` (режим: **free** для Issues; код почти не нужен)
- Только Issues + Boards + Milestones
- Эпики: «Каталог запчастей v2», «Публичный API atris.site», «GitLab CI prod»
- Labels: `product::blog`, `product::site`, `ops`, `P0`/`P1`/`P2`
- Board колонки: `Backlog` → `Ready` → `In progress` → `Review` → `Done`

Кросс-командные задачи живут здесь; реализация — в продуктовом репо (связь через Related issues).

---

### 3.2. `atris/products` — продукты

#### `blog-platform` — **strict**
Монорепо (как сейчас): сервисы, gateway, UI, `docker-compose`, `.gitlab-ci.yml`.

Issues-примеры:
- `feat: фильтр постов по тегу`
- `bug: 502 на public-gateway`
- `chore: обновить Java 21 image в CI`

Ветки: `feature/*`, `fix/*`, `chore/*` → MR в `main`.

#### `atris-site` — **strict** (или free, если только контент)
- Конфиги для Tilda / embed
- Статика, скрипты виджетов
- Документация «как встроить API»

Если сайт целиком на Tilda без кода — репо всё равно полезен как **Issues + чеклисты** («поменять блок на главной»).

#### `mobile-hub` — **free** на этапе концепта
- Перенести `docs/app-concept/`
- Дизайн, PDF, HTML-презентация
- Issues: roadmap экранов, feedback партнёров  
Когда начнётся разработка — включить **strict**.

---

### 3.3. `atris/ops` — эксплуатация

#### `infra` — **strict** (только вы мержите)
Содержимое (можно постепенно переносить из `deploy/`):
```text
infra/
  gitlab/           # docker-compose, scripts/protect-project.sh
  nginx/            # git.atris.site, api.atris.site, …
  vps/              # заметки: swap, firewall, DNS
  compose/          # prod compose / .env.example
  README.md         # карта серверов и доменов
```

Issues: «добавить swap», «обновить certbot», «новый subdomain».

#### `runbooks` — **free** (правят все, кто дежурит)
Пошаговые инструкции:
- GitLab 502 / OOM
- API не отвечает
- Восстановление из backup
- Выпуск SSL

Формат: один md = один инцидент-сценарий.

#### `monitoring` — по мере надобности
Скрипты health-check, список URL для проверки, будущие алерты.

---

### 3.4. `atris/work` — операционка без «тяжёлого» кода

#### `support` — тикеты
- Issue = обращение клиента / партнёра
- Labels: `status::new`, `status::waiting`, `priority::*`
- В описании: контакт, суть, ссылка на Issue в продукте после эскалации
- Board: New → Triage → In progress → Waiting client → Done

#### `content`
- ТЗ на посты, тексты каталога, SEO
- Черновики md (не секреты)
- Связь: Issue → MR/задача в `blog-platform` на публикацию через API

#### `partners`
- Материалы для партнёров (без паролей)
- Чеклисты встреч
- Версии КП / презентаций (крупные бинарники — Git LFS или ссылка на диск)

---

### 3.5. `atris/archive`
Старые репо: **Archive project** в GitLab + visibility только Maintainer.  
Никаких новых Issues; только история.

---

## 4. Единая система меток (Labels)

Создайте на уровне группы `atris` (наследуются):

### Тип
| Label | Цвет (пример) | Смысл |
|-------|---------------|--------|
| `type::feature` | синий | новая функция |
| `type::bug` | красный | дефект |
| `type::chore` | серый | техдолг, рефакторинг |
| `type::docs` | зелёный | документация |
| `type::support` | оранжевый | внешний запрос |

### Приоритет
| Label | Смысл |
|-------|--------|
| `P0` | горит, всё бросаем |
| `P1` | этот спринт |
| `P2` | бэклог |
| `P3` | идея |

### Область
| Label | Смысл |
|-------|--------|
| `area::api` | gateway / сервисы |
| `area::admin-ui` | админка |
| `area::public` | публичный сайт/API |
| `area::infra` | сервер / GitLab / nginx |
| `area::ci` | пайплайны |

### Статус (для Boards)
`status::backlog` · `status::ready` · `status::doing` · `status::review` · `status::blocked` · `status::done`

---

## 5. Boards (доски) — минимум 3

### A. Product board — в `roadmap` или в `blog-platform`
Колонки по `status::*`  
Фильтр: `product::blog` или milestone текущего спринта.

### B. Ops board — в `ops/infra`
Инфра-задачи, инциденты (`P0`).

### C. Support board — в `work/support`
Входящие обращения.

Один человек может смотреть все доски; разработчик обычно живёт в Product board.

---

## 6. Права по типам репо (скрипт `protect-project.sh`)

| Репозиторий | mode | Кто мержит `main` |
|-------------|------|-------------------|
| `products/blog-platform` | **strict** | вы / Maintainer |
| `products/atris-site` | **strict** | вы |
| `products/mobile-hub` | free | Developers (пока концепт) |
| `ops/infra` | **strict** | только вы |
| `ops/runbooks` | free | команда |
| `_meta/handbook` | free | команда (критичное — MR + вас в reviewers) |
| `_meta/templates` | free | команда |
| `_meta/roadmap` | free | Issues важнее кода |
| `work/*` | free / open | операционка |
| `archive/*` | — | archived |

Список для скрипта:

```text
atris/products/blog-platform    strict
atris/products/atris-site       strict
atris/ops/infra                 strict
atris/products/mobile-hub       free
atris/ops/runbooks              free
atris/_meta/handbook            free
atris/_meta/templates           free
atris/work/support              open
atris/work/content              open
```

---

## 7. Жизненный цикл одной задачи (сквозной)

Пример: «На публичном API не отдаётся cover у поста».

```text
1. Клиент пишет → Issue в work/support (#12)
2. Триаж → Related: создаём bug в products/blog-platform (#88), label type::bug P1
3. Dev: branch fix/cover-url → коммиты → MR !15
4. CI: build + test (зелёный)
5. Вы: Approve + Merge (strict)
6. Deploy (manual job или runbook)
7. Закрываем #88 и #12, комментарий клиенту
```

Всё видно в GitLab, без отдельного «чат-трекера» как единственного источника правды.

---

## 8. Что создать в первую очередь (порядок)

### День 1 — каркас
1. Группы: `_meta`, `products`, `ops`, `work`, `archive`
2. Репо: `handbook`, `roadmap`, `blog-platform` (миграция), `infra`
3. Labels на группе `atris`
4. Board в `roadmap`
5. Protected branches скриптом для `blog-platform` + `infra` = strict

### День 2 — процессы
6. Issue / MR templates в `templates` (или в каждом репо `.gitlab/`)
7. `support` + Support board
8. Перенос `deploy/*` → `ops/infra` (или submodule / копия с историей)
9. Онбординг-Issue в `handbook` для каждого нового человека

### Неделя 2
10. `atris-site`, `content`, `runbooks`
11. CI Runner + green pipeline на `blog-platform`
12. Milestone «Sprint 1» в `roadmap`

---

## 9. Шаблоны Issue (положите в `.gitlab/issue_templates/`)

**Bug.md**
```markdown
## Симптом
## Как воспроизвести
## Ожидание / факт
## Окружение (prod/staging, URL)
## Связанные MR / логи
```

**Feature.md**
```markdown
## Зачем (проблема пользователя)
## Предложение
## Критерии готовности (DoD)
## Вне скоупа
```

**Support.md**
```markdown
## Клиент / контакт
## Запрос
## Срок
## Эскалация в продукт (ссылка на Issue)
```

**MR default**
```markdown
## Что сделано
## Как проверить
## Screenshots / curl
## Checklist
- [ ] Self-review
- [ ] Нет секретов в коммите
- [ ] Связанный Issue #
```

---

## 10. Чего не делать

- Не складывать **все** Issues только в один гигантский репо — теряется контекст.
- Не хранить пароли/`.env` в Git — только `.env.example`.
- Не делать 30 микрорепозиториев «на каждый сервис» на старте: у вас уже удачный **монорепо** `blog-platform`.
- Не дублировать задачи в Telegram как единственный трекер — в чате ссылка на Issue.

---

## 11. Карта «роль → где жить каждый день»

| Роль | Основные места |
|------|----------------|
| Вы (админ) | `roadmap` Board, MR на `strict` репо, `ops/infra` |
| Backend/Fullstack | `blog-platform` Issues + MR |
| Контент / маркетинг | `work/content`, иногда `atris-site` |
| Поддержка | `work/support` |
| Все | `handbook` при вопросе «как у нас принято» |

---

## 12. Итоговая схема для создания в UI

```text
New group: atris
  New subgroup: _meta
    New project: handbook
    New project: templates
    New project: roadmap
  New subgroup: products
    New project: blog-platform      ← push текущего кода
    New project: atris-site
    New project: mobile-hub
  New subgroup: ops
    New project: infra
    New project: runbooks
  New subgroup: work
    New project: support
    New project: content
    New project: partners
  New subgroup: archive
```

После создания — прогнать `protect-project.sh --from-list` с режимами из §6.

---

Эта структура закрывает: разработку, ревью, релизы, инциденты, поддержку, контент, онбординг и планирование — всё внутри GitLab, с разным уровнем контроля (`strict`/`free`) по критичности.
