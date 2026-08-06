# Полный гайд: настройка GitLab для команды Atris

**Инстанс:** https://git.atris.site  
**Сервер:** `vm-v2-mini`, каталог `/srv/gitlab`  
**SSH clone:** порт **2222** (не 22)  
**Стек рядом:** blog-platform (Docker) на том же VPS (~8 GB RAM)

Этот гайд — **настройка после установки**. Установка уже сделана (Docker + nginx + HTTPS).  
Идите по разделам по порядку. Каждый блок = конкретные действия в UI или на сервере.

---

## Содержание

1. [Проверка, что всё живо](#1-проверка-что-всё-живо)
2. [Память и стабильность VPS](#2-память-и-стабильность-vps)
3. [Безопасность root и регистрация](#3-безопасность-root-и-регистрация)
4. [Структура групп и проектов](#4-структура-групп-и-проектов)
5. [Пользователи и роли](#5-пользователи-и-роли)
6. [Защита main и одобрение merge](#6-защита-main-и-одобрение-merge)
7. [Первый проект и миграция](#7-первый-проект-и-миграция)
8. [SSH и HTTPS для разработчиков](#8-ssh-и-https-для-разработчиков)
9. [Cursor / CodeX](#9-cursor--codex)
10. [CI/CD Runner и пайплайны](#10-cicd-runner-и-пайплайны)
11. [Совместная работа в браузере](#11-совместная-работа-в-браузере)
12. [Бэкапы и обновления](#12-бэкапы-и-обновления)
13. [Ежедневный workflow команды](#13-ежедневный-workflow-команды)
14. [Чеклист готовности](#14-чеклист-готовности)

---

## 1. Проверка, что всё живо

На сервере:

```bash
cd /srv/gitlab
docker compose ps
curl -sI https://git.atris.site | head -10
free -h
```

Ожидаемо:

| Проверка | Результат |
|----------|-----------|
| Контейнер `gitlab` | `Up` / `healthy` |
| HTTPS | `302` → `/users/sign_in` или `200` |
| Swap | `4.0Gi` (уже добавлен) |
| available RAM | желательно ≥ 1–2 GiB |

Откройте в браузере: **https://git.atris.site**

---

## 2. Память и стабильность VPS

На одном VPS крутятся **GitLab + blog-platform**. Без лимитов GitLab легко съедает 3–4+ GiB.

### 2.1. Swap (если ещё нет)

```bash
free -h
# если Swap: 0B — выполнить:
fallocate -l 4G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
grep -q swapfile /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

### 2.2. Урезать GitLab под 8 GB

Отредактируйте `/srv/gitlab/docker-compose.yml`:

```yaml
environment:
  GITLAB_OMNIBUS_CONFIG: |
    external_url 'https://git.atris.site'
    nginx['listen_port'] = 80
    nginx['listen_https'] = false
    nginx['proxy_set_headers'] = {
      "X-Forwarded-Proto" => "https",
      "X-Forwarded-Ssl" => "on"
    }
    gitlab_rails['gitlab_shell_ssh_port'] = 2222
    puma['worker_processes'] = 1
    sidekiq['max_concurrency'] = 5
    prometheus_monitoring['enable'] = false
```

Применить:

```bash
cd /srv/gitlab
docker compose up -d
# подождать 3–5 мин
docker stats --no-stream gitlab
free -h
```

Цель: GitLab стабильно около **2–3 GiB**, а не 4+.

### 2.3. Мониторинг

```bash
docker stats --no-stream
ps aux --sort=-%mem | head -20
dmesg -T | grep -Ei "killed process|out of memory|oom" || true
```

---

## 3. Безопасность root и регистрация

### 3.1. Вход и смена пароля

1. Откройте https://git.atris.site
2. Логин: `root`
3. Пароль: из `docker exec -it gitlab grep 'Password:' /etc/gitlab/initial_root_password`  
   (если файла нет — сброс ниже)
4. **Avatar (справа вверху) → Edit profile → Password** → смените пароль
5. Сохраните новый пароль в менеджере паролей (не в чатах)

Сброс, если забыли:

```bash
docker exec -it gitlab gitlab-rake "gitlab:password:reset[root]"
```

### 3.2. Отключить публичную регистрацию

1. **Admin Area** (иконка гаечного ключа / wrench)
2. **Settings → General → Sign-up restrictions**
3. Снимите галочку **Sign-up enabled**
4. **Save changes**

Дальше пользователей создаёте только вы.

### 3.3. (Рекомендуется) Второй админ

1. **Admin Area → Users → New user**
2. Email, username, Name
3. После создания: **Edit → Access level → Admin**
4. Отправьте пользователю ссылку на сброс пароля / задайте пароль вручную
5. В повседневной работе используйте этого пользователя, `root` — только для аварий

---

## 4. Структура групп и проектов

### 4.1. Корневая группа

1. **Menu → Groups → Create group**
2. Group name: `atris`
3. Group URL: `atris` → будет `https://git.atris.site/atris`
4. Visibility: **Private**
5. Create group

### 4.2. Рекомендуемая структура

```text
atris/                          # компания
├── blog-platform               # текущий монорепо
├── atris-site                  # сайт / интеграции (если отдельно)
├── infra                       # nginx, docker-compose prod, скрипты
├── templates/
│   ├── spring-service
│   └── vue-app
└── archive/                    # старые проекты (read-only)
```

Подгруппы:

1. Откройте группу `atris`
2. **New subgroup** → `templates`, `archive` и т.д.

Правила именования:

- `kebab-case`: `blog-platform`, `parts-catalog`
- default branch: `main`
- visibility: **Private**
- в каждом проекте: `README.md` + `.env.example` (без секретов)

---

## 5. Пользователи и роли

### 5.1. Создать пользователей

**Admin Area → Users → New user** для каждого человека:

| Поле | Пример |
|------|--------|
| Name | Иван Иванов |
| Username | ivan |
| Email | ivan@atris.site |

После создания задайте пароль (Edit user → Password) или отправьте invite.

### 5.2. Добавить в группу

1. Группа **atris → Manage → Members → Invite members**
2. Выберите пользователя и роль:

| Роль в команде | Роль в GitLab | Что может |
|----------------|---------------|-----------|
| Наблюдатель | Guest / Reporter | Смотреть issues (ограниченно) |
| Разработчик | **Developer** | Ветки, push, MR; **не** merge в `main` |
| Ревьюер / тимлид | **Maintainer** | Approve + merge в `main`, настройки проекта |
| Админ компании | **Owner** (группа) | Пользователи группы, всё |

### 5.3. Матрица доступа (зафиксируйте у себя)

| Человек | Username | Роль GitLab | Может merge в main |
|---------|----------|-------------|--------------------|
| … | … | Owner | да |
| … | … | Maintainer | да |
| … | … | Developer | нет |

---

## 6. Защита main и одобрение merge

Цель: в `main` нельзя пушить напрямую; слияние только через MR после одобрения админа/тимлида.

Делайте на уровне **группы** `atris` (наследуется проектами).

### 6.1. Protected branch

1. Группа **atris → Settings → Repository → Protected branches**
2. **Add protected branch** / Protect a branch:

| Параметр | Значение |
|----------|----------|
| Branch | `main` (или `*` / `main` Exact) |
| Allowed to merge | **Maintainers** |
| Allowed to push and merge | **No one** |
| Allowed to force push | **Off** |

3. Protect / Save

Проверка: Developer делает `git push origin main` → должен получить отказ.

### 6.2. Merge request approvals

1. **Settings → Merge requests** (группы или проекта)
2. Раздел **Merge request approvals** → Add approval rule:

| Параметр | Значение |
|----------|----------|
| Rule name | `Admins` |
| Target branch | All / `main` |
| Approvals required | **1** (для критичных репо — 2) |
| Approvers | пользователи Maintainer / Owner |

3. Включите **Prevent approval by author** (автор MR не одобряет сам себя)
4. Save

### 6.3. Дополнительные настройки MR (рекомендуется)

В **Settings → Merge requests**:

- Squash commits when merging — по желанию
- Enable «Delete source branch when merge request is accepted»
- Merge method: Merge commit или Fast-forward + merge commit

### 6.4. Как выглядит правильный процесс

```text
Developer:
  feature/login → push → Create Merge Request → target: main

Maintainer:
  Review → Approve → Merge
```

Без Approve кнопка Merge недоступна (или merge запрещён правилами).

---

## 7. Первый проект и миграция

### 7.1. Создать проект в GitLab

1. Группа **atris → New project → Create blank project**
2. Project name: `blog-platform`
3. Visibility: Private
4. **Не** инициализируйте README, если пушите существующий репозиторий с историей
5. Create project

Скопируйте URL:

- HTTPS: `https://git.atris.site/atris/blog-platform.git`
- SSH: `git@git.atris.site:atris/blog-platform.git`

### 7.2. Миграция существующего репозитория (с ПК)

В каталоге `blog-platform` на вашем компьютере:

```bash
cd /path/to/blog-platform

# посмотреть текущие remotes
git remote -v

# добавить GitLab (не удаляя старый origin, если он ещё нужен)
git remote add gitlab https://git.atris.site/atris/blog-platform.git

# или сразу сделать GitLab основным:
# git remote rename origin old-origin
# git remote add origin https://git.atris.site/atris/blog-platform.git

git push -u gitlab --all
git push gitlab --tags
```

Если используете SSH (после настройки ключей, раздел 8):

```bash
git remote set-url gitlab git@git.atris.site:atris/blog-platform.git
git push -u gitlab --all
```

### 7.3. Миграция с GitHub / другого Git (зеркало)

```bash
git clone --mirror https://github.com/ORG/REPO.git
cd REPO.git
git remote set-url origin https://git.atris.site/atris/NEW-NAME.git
git push --mirror
```

### 7.4. После пуша

1. Убедитесь, что ветка `main` protected
2. Approvals включены
3. Создайте тестовый MR с Developer-аккаунта и проверьте Approve → Merge

### 7.5. Чеклист миграции одного проекта

- [ ] Проект создан в `atris`
- [ ] История и теги перенесены
- [ ] `main` protected, push = No one
- [ ] MR approvals ≥ 1, author не одобряет себя
- [ ] README обновлён (новый clone URL)
- [ ] Команда переключила `origin` на GitLab
- [ ] Старый remote archived / только read-only

---

## 8. SSH и HTTPS для разработчиков

### 8.1. SSH (рекомендуется)

На ПК разработчика:

```bash
# ключ, если нет
ssh-keygen -t ed25519 -C "ivan@atris.site"

# показать публичный ключ
cat ~/.ssh/id_ed25519.pub
```

В GitLab: **Preferences (Avatar) → SSH Keys** → вставить ключ → Add key.

Файл `~/.ssh/config`:

```sshconfig
Host git.atris.site
  HostName git.atris.site
  Port 2222
  User git
  IdentityFile ~/.ssh/id_ed25519
```

Проверка:

```bash
ssh -T git@git.atris.site
# Welcome to GitLab, @ivan!
```

Клон:

```bash
git clone git@git.atris.site:atris/blog-platform.git
```

### 8.2. HTTPS + Personal Access Token

Если SSH неудобен:

1. **Preferences → Access Tokens**
2. Name: `laptop`
3. Scopes: `read_repository`, `write_repository` (для API ещё `api`)
4. Create → скопировать токен один раз

При `git push` / `git pull`:

- Username = ваш username в GitLab
- Password = **токен** (не пароль от сайта)

---

## 9. Cursor / CodeX

> Cursor и CodeX **не интегрируются** с GitLab отдельным плагином. Они работают с **локальной папкой Git**. Совместная работа команды — через ветки и Merge Request.

### Для каждого разработчика

1. Клонировать репозиторий (SSH или HTTPS)
2. **Cursor → File → Open Folder** → папка проекта
3. Работа в feature-ветке, коммиты, push
4. В GitLab создать Merge Request

```bash
git checkout main
git pull origin main
git checkout -b feature/short-name
# правки в Cursor / CodeX
git add -A
git commit -m "feat: описание"
git push -u origin feature/short-name
```

Затем в GitLab: **Create merge request**.

### «Реальное время»

| Способ | Когда |
|--------|-------|
| Merge Request + комментарии к строкам | основной процесс |
| GitLab Web IDE | быстрые правки в браузере |
| Созвон + шаринг экрана | парное программирование |

---

## 10. CI/CD Runner и пайплайны

### 10.1. Установить GitLab Runner на VPS

```bash
curl -L https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh | sudo bash
sudo apt install -y gitlab-runner
```

### 10.2. Зарегистрировать Runner

1. В GitLab: **Admin Area → CI/CD → Runners → New instance runner**  
   (или Project → Settings → CI/CD → Runners)
2. Скопируйте registration token
3. На сервере:

```bash
sudo gitlab-runner register \
  --url https://git.atris.site \
  --token ВАШ_ТОКЕН \
  --executor docker \
  --docker-image eclipse-temurin:21-jdk \
  --description "vm-v2-mini" \
  --non-interactive
```

Для Docker-сборок может понадобиться доступ к docker.sock (осторожно с правами).  
Проверка: **Settings → CI/CD → Runners** — runner зелёный (online).

### 10.3. Файл пайплайна в репозитории

В корне `blog-platform` уже есть `.gitlab-ci.yml` (build / test / docker / deploy).

После push в GitLab: **Build → Pipelines** — должен появиться pipeline.

### 10.4. CI Variables (секреты)

**Project → Settings → CI/CD → Variables** (Expand):

| Key | Flags | Назначение |
|-----|-------|------------|
| `SSH_PRIVATE_KEY` | Masked, Protected | ключ для деплоя |
| `DEPLOY_HOST` | Protected | IP или hostname VPS |
| `DEPLOY_USER` | Protected | например `root` или `deploy` |

Не коммитьте `.env` с паролями.

### 10.5. Рекомендуемый CI flow

```text
MR / push feature  →  build + test
merge в main       →  build + test + (manual) docker + (manual) deploy
```

На маленьком VPS не запускайте тяжёлые `docker compose build` автоматически на каждый коммит — оставьте **manual**.

---

## 11. Совместная работа в браузере

1. Откройте проект на https://git.atris.site
2. **Code → Open with → Web IDE** (или Edit → Web IDE)
3. Создайте ветку / переключитесь
4. Правите файлы → **Source Control → Commit**
5. **Create merge request**
6. Коллега: MR → Review → комментарии к строкам → Approve → Merge (если Maintainer)

Конфликты: кнопка **Resolve conflicts** в MR или локально в Cursor.

---

## 12. Бэкапы и обновления

### 12.1. Ручной бэкап

```bash
docker exec -t gitlab gitlab-backup create
ls -lah /srv/gitlab/data/backups/
```

### 12.2. Cron раз в сутки (пример 03:00)

```bash
crontab -e
```

Строка:

```cron
0 3 * * * docker exec gitlab gitlab-backup create CRON=1 >> /var/log/gitlab-backup.log 2>&1
```

Периодически копируйте `/srv/gitlab/data/backups/` и `/srv/gitlab/config/` на другое хранилище.

### 12.3. Обновление GitLab

```bash
cd /srv/gitlab
docker compose pull
docker compose up -d
docker compose logs -f --tail=50 gitlab
```

Перед обновлением — бэкап.

---

## 13. Ежедневный workflow команды

### Разработчик (Developer)

```bash
git checkout main && git pull
git checkout -b feature/login-form
# работа в Cursor
git add -A && git commit -m "feat: login form"
git push -u origin feature/login-form
```

В GitLab → **Create merge request** → Assignee / Reviewer = тимлид.

### Ревьюер / тимлид (Maintainer)

1. Открыть MR
2. Проверить Diff, комментарии
3. Дождаться зелёного pipeline (если CI настроен)
4. **Approve**
5. **Merge**
6. Удалить source branch (если включено)

### Запрещено

- `git push` напрямую в `main`
- Force push в `main`
- Одобрять свой собственный MR

---

## 14. Чеклист готовности

### Инфраструктура

- [ ] https://git.atris.site открывается
- [ ] Swap 4 GB включён
- [ ] GitLab урезан (`puma=1`, `sidekiq=5`) при нехватке RAM
- [ ] Пароль root сменён
- [ ] Публичная регистрация выключена
- [ ] Cron бэкапа настроен

### Организация

- [ ] Группа `atris` создана
- [ ] Структура проектов согласована
- [ ] Все члены команды добавлены с ролями
- [ ] `main` protected: push = No one, merge = Maintainers
- [ ] MR approvals ≥ 1, Prevent approval by author = ON

### Разработка

- [ ] `blog-platform` (и другие) запушены в GitLab
- [ ] У команды работает SSH (порт 2222) или HTTPS+PAT
- [ ] Cursor открывает локальный клон, push идёт в GitLab
- [ ] Тестовый MR: Developer → Approve Maintainer → Merge

### CI/CD

- [ ] Runner online
- [ ] Pipeline build/test зелёный на MR
- [ ] CI Variables заданы
- [ ] Deploy — только manual / после ревью

### Документация

- [ ] Команде выдан этот гайд или `docs/GITLAB_TEAM_GUIDE.md`
- [ ] В README проекта указан новый clone URL

---

## Быстрые команды (шпаргалка сервера)

```bash
# статус
cd /srv/gitlab && docker compose ps
docker stats --no-stream
free -h

# логи GitLab
docker compose logs -f --tail=100 gitlab

# пароль root (только первый день)
docker exec -it gitlab grep 'Password:' /etc/gitlab/initial_root_password

# сброс пароля root
docker exec -it gitlab gitlab-rake "gitlab:password:reset[root]"

# бэкап
docker exec -t gitlab gitlab-backup create

# nginx
nginx -t && systemctl reload nginx
certbot renew --dry-run
```

---

## Связанные файлы в репозитории

| Файл | Назначение |
|------|------------|
| `deploy/GITLAB_ATRIS.md` | Установка с нуля |
| `deploy/gitlab/docker-compose.yml` | Compose GitLab |
| `deploy/nginx/git.atris.site.conf` | Nginx |
| `.gitlab-ci.yml` | CI/CD для blog-platform |
| `docs/GITLAB_TEAM_GUIDE.md` | Краткая версия для команды |
| **этот файл** | Полная настройка после установки |

---

## Порядок работ на ближайшие дни

| День | Что сделать |
|------|-------------|
| **Сегодня** | §3 безопасность, §4 группа, §5 пользователи, §6 protected + approvals |
| **Сегодня / завтра** | §2 урезать GitLab если RAM снова жмёт; §7 миграция blog-platform |
| **Завтра** | §8 SSH у всех; §9 Cursor; тестовый MR |
| **Эта неделя** | §10 Runner + green pipeline; §12 бэкап cron |
| **Потом** | Остальные проекты, шаблоны, deploy job |

Если застрянете на конкретном шаге — пришлите скрин/вывод команды с номером раздела (§), подскажу точечно.
