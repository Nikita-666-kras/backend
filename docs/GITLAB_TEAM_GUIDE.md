# GitLab для команды Atris — гайд и чеклист

**Инстанс:** https://git.atris.site  
**SSH clone:** порт `2222` (не 22)

---

## Статус задач

| # | Задача | Статус | Действие |
|---|--------|--------|----------|
| 1 | Self-hosted GitLab | ✅ Готово | Дальше: swap, бэкапы, обновления |
| 2 | Cursor + CodeX | ⏳ Настроить | Git remote + PAT/SSH (см. ниже) |
| 3 | Структура репозиториев | ⏳ Создать группы | Схема в разделе 3 |
| 4 | Роли и доступ | ⏳ Настроить | Protected branches + approvals |
| 5 | CI/CD | ⏳ Runner + `.gitlab-ci.yml` | Раздел 5 |
| 6 | Веб-редактирование | ✅ Встроено в GitLab | Web IDE + MR |
| 7 | Документация | ✅ Этот файл | Раздать команде |
| 8 | Миграция | ⏳ По проектам | Раздел 8 |

---

## 1. GitLab (уже развёрнут)

- URL: https://git.atris.site
- Сервер: `/srv/gitlab`, `docker compose`
- HTTPS: Let's Encrypt до 2026-11-04

**Админу — доделать:**

```bash
# swap (RAM ~7.7 GB, GitLab тяжёлый)
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# бэкап (cron раз в сутки)
docker exec -t gitlab gitlab-backup create
```

Отключить публичную регистрацию: **Admin → Settings → Sign-up restrictions**.

---

## 2. Cursor и CodeX — как подключить

> **Важно:** Cursor и CodeX (AI-ассистенты) **не заменяют** GitLab и **не дают** Google Docs–style редактирование в одном файле у двух людей одновременно. Совместная работа идёт через **Git + Merge Request**.

### Cursor (IDE)

1. Клонировать репозиторий:

```bash
git clone https://git.atris.site/atris/blog-platform.git
# или SSH (см. ниже)
```

2. **HTTPS + Personal Access Token (PAT):**
   - GitLab → **Preferences → Access Tokens**
   - Scopes: `read_repository`, `write_repository`
   - При `git push` логин = ваш username, пароль = PAT

3. **SSH (рекомендуется):**

```bash
# ~/.ssh/config
Host git.atris.site
  HostName git.atris.site
  Port 2222
  User git
  IdentityFile ~/.ssh/id_ed25519
```

Ключ: **Preferences → SSH Keys**.

4. В Cursor: **File → Open Folder** → работаете как обычно; AI (Agent/Chat) видит локальный код. Push/Pull — через встроенный Git или терминал.

### CodeX / другие AI-инструменты

Любой инструмент, который работает с **локальной папкой Git**, подключается так же:

- клон с `git.atris.site`
- ветки → коммиты → MR в GitLab

Отдельной «официальной интеграции GitLab ↔ Cursor» не требуется — достаточно remote `origin`.

### «Реальное время» между людьми

| Способ | Когда использовать |
|--------|-------------------|
| **Merge Request + комментарии** | Основной процесс ревью |
| **GitLab Web IDE** | Быстрая правка в браузере без установки IDE |
| **Общий экран / созвон** | Парное программирование |
| Cursor Live Share | Не встроен; при необходимости — отдельный инструмент |

---

## 3. Структура репозиториев

Рекомендуемая иерархия групп на `git.atris.site`:

```text
atris/                          # корневая группа компании
├── blog-platform/              # текущий монорепо (микросервисы)
├── atris-site/                 # фронт / Tilda-интеграции (если отдельно)
├── infra/                      # nginx, deploy-скрипты, docker-compose prod
│   └── gitlab/                 # (опционально) IaC для GitLab
├── templates/
│   ├── spring-service/         # шаблон нового Java-сервиса
│   └── vue-app/                # шаблон UI
└── archive/                    # legacy / read-only проекты
```

**Правила:**

- Один продукт = один репозиторий (или монорепо, если сервисы деплоятся вместе — как `blog-platform`).
- Имена: `kebab-case`, без пробелов.
- Default branch: `main`.
- README + `.env.example` в каждом репо.

**Создание (Maintainer):**

1. **Groups → atris → New subgroup** (при необходимости)
2. **New project → Create blank project**
3. Visibility: **Private**
4. Initialize with README — по желанию

---

## 4. Роли и права

### Роли GitLab

| Роль в GitLab | Кто | Права |
|---------------|-----|-------|
| **Guest** | Заказчики, наблюдатели | Issues, чтение (если разрешено) |
| **Reporter** | Аналитики | + issues, wiki |
| **Developer** | Разработчики | Push в ветки, создание MR, не merge в `main` |
| **Maintainer** | Тимлид, ревьюер-админ | Merge в protected, настройки репо |
| **Owner** | Вы (админ GitLab) | Всё + группа |

### Матрица «роль в команде → GitLab»

| Человек | GitLab role | Merge в main |
|---------|-------------|--------------|
| Junior/Middle dev | Developer | ❌ только через MR |
| Senior / Reviewer | Developer или Maintainer | ✅ Approve; merge — Maintainer |
| Team lead | Maintainer | ✅ Approve + Merge |
| DevOps / Admin | Owner (группа) | ✅ + CI/CD variables, runners |

### Обязательные настройки (группа **atris**)

**Settings → Repository → Protected branches** (`main`):

- Allowed to push: **No one**
- Allowed to merge: **Maintainers**

**Settings → Merge requests → Approvals**:

- Required approvals: **1** (или 2 для критичных репо)
- Approvers: Maintainers / группа «Reviewers»
- **Prevent approval by author** — ON

**Settings → Repository → Push rules** (если доступно в CE — частично; в EE больше опций):

- Запрет force-push на protected — уже через protected branch

---

## 5. CI/CD

### 5.1. GitLab Runner на сервере

На VPS (рядом с GitLab):

```bash
curl -L https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh | sudo bash
sudo apt install gitlab-runner

# Registration token: Admin → CI/CD → Runners → New instance runner
sudo gitlab-runner register \
  --url https://git.atris.site \
  --token ВАШ_TOKEN \
  --executor docker \
  --docker-image docker:24 \
  --docker-volumes /var/run/docker.sock:/var/run/docker.sock \
  --description "vm-v2-mini-docker"
```

В проекте: **Settings → CI/CD → Runners** — runner должен быть **green**.

### 5.2. Пример пайплайна (blog-platform)

В корне репозитория файл `.gitlab-ci.yml` (см. репозиторий). Этапы:

1. **build** — `./gradlew build -x test`
2. **test** — `./gradlew test`
3. **docker** — сборка образов (manual / только `main`)
4. **deploy** — SSH на VPS + `docker compose up -d` (manual, protected)

### 5.3. Секреты CI

**Settings → CI/CD → Variables** (masked + protected):

| Variable | Назначение |
|----------|------------|
| `SSH_PRIVATE_KEY` | Деплой на VPS |
| `DEPLOY_HOST` | IP или `api.atris.site` |
| `POST_DB_PASSWORD` и др. | Prod `.env` (лучше отдельный vault) |

Не коммитить секреты в Git.

### 5.4. Типичный flow

```text
push feature/* → pipeline: build + test
MR в main      → pipeline на MR
merge main     → build + test + (manual) deploy staging/prod
```

---

## 6. Совместное редактирование в браузере

1. Откройте проект на https://git.atris.site
2. **Code → Open Web IDE** (или Edit → Web IDE)
3. Создайте ветку, правьте файлы, **Commit** → **Create merge request**
4. Коллега ревьюит diff в MR, оставляет комментарии к строкам

Для конфликтов: **Resolve conflicts** в UI или локально в Cursor.

---

## 7. Ежедневный workflow для разработчика

```bash
git checkout main && git pull origin main
git checkout -b feature/short-description

# ... правки в Cursor ...

git add -A && git commit -m "feat: описание"
git push -u origin feature/short-description
```

В GitLab: **Create merge request** → target `main` → assign reviewer → после **Approve** Maintainer делает **Merge**.

### Полезные ссылки

- Проект: `https://git.atris.site/atris/<repo>`
- MR: `https://git.atris.site/atris/<repo>/-/merge_requests`
- Pipelines: `https://git.atris.site/atris/<repo>/-/pipelines`

---

## 8. Миграция с других систем

### С GitHub / GitLab.com / Bitbucket

**Вариант A — Import (UI):**  
**New project → Import project** → URL + token.

**Вариант B — git mirror (сохраняет все ветки и теги):**

```bash
git clone --mirror https://github.com/org/old-repo.git
cd old-repo.git
git remote set-url origin https://git.atris.site/atris/new-repo.git
git push --mirror
```

### blog-platform (текущий репозиторий)

```bash
cd blog-platform
git remote add gitlab https://git.atris.site/atris/blog-platform.git
git push -u gitlab --all
git push gitlab --tags
```

После миграции: protected `main`, включить CI, обновить `origin` у команды.

### Чеклист миграции одного проекта

- [ ] Создан проект в группе `atris`
- [ ] История и теги перенесены
- [ ] Protected branch `main`
- [ ] MR approvals
- [ ] `.gitlab-ci.yml` + runner
- [ ] CI variables
- [ ] README обновлён (новый clone URL)
- [ ] Старый remote отключён или archived

---

## План работ по неделям (ориентир)

| Неделя | Задачи |
|--------|--------|
| **1** | ✅ GitLab up; группа `atris`; пользователи; protected main; swap + backup |
| **2** | Миграция `blog-platform`; Runner; первый green pipeline |
| **3** | Шаблоны репо; onboarding 2–3 dev; Cursor/SSH инструкция в Slack/Notion |
| **4** | Deploy job (staging); документация; ретrospective процесса MR |

---

## Контакты и поддержка

- Админ GitLab: `@root` / назначенный Owner
- Сервер: `root@vm-v2-mini`, GitLab: `/srv/gitlab`
- Полная установка: `deploy/GITLAB_ATRIS.md`
