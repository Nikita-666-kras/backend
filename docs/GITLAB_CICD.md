# CI/CD для blog-platform на git.atris.site

Пайплайн уже описан в корне: **`.gitlab-ci.yml`**.  
Ниже — как включить его на вашем VPS (GitLab + приложение на одной машине ~8 GB RAM).

---

## Как это устроено

```text
Push / MR
   ↓
GitLab → берёт .gitlab-ci.yml
   ↓
GitLab Runner (на VPS) выполняет jobs в Docker
   ↓
stages: build → test → (manual) docker → (manual) deploy
```

| Stage | Когда | Что делает |
|-------|--------|------------|
| `build` | MR и `main` | собирает Java (Gradle) |
| `test` | MR и `main` | прогоняет тесты |
| `docker:build` | только `main`, **вручную** | `docker compose build` |
| `deploy:production` | только `main`, **вручную** | SSH на сервер → `git pull` + `compose up` |

На маленьком VPS тяжёлые docker/deploy **не** запускаются на каждый коммит — только по кнопке.

---

## Шаг 1. Убедиться, что проект в GitLab

```bash
# на ПК
git remote -v
# должен быть git.atris.site/.../blog-platform
```

В репозитории должны быть:
- `.gitlab-ci.yml`
- Gradle Wrapper (`gradlew`, `gradle/wrapper/`) — если нет, см. шаг 1b

### 1b. Если нет `gradlew`

На ПК в корне проекта:

```bash
gradle wrapper --gradle-version 8.10.2
git add gradlew gradlew.bat gradle/wrapper
git commit -m "chore: add Gradle wrapper for CI"
git push
```

Без wrapper CI упадёт на `./gradlew`.

---

## Шаг 2. Установить GitLab Runner на VPS

На `vm-v2-mini`:

```bash
curl -L https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh | sudo bash
sudo apt install -y gitlab-runner
sudo gitlab-runner --version
```

---

## Шаг 3. Зарегистрировать Runner

### 3.1. Взять токен в GitLab

**Вариант A — на весь инстанс (удобнее):**  
Admin Area (гаечный ключ) → **CI/CD → Runners → Create instance runner**  
Скопируйте token (`glrt-...`).

**Вариант B — только на проект:**  
Проект `blog-platform` → **Settings → CI/CD → Runners → New project runner**.

### 3.2. Регистрация (Docker executor)

```bash
sudo gitlab-runner register \
  --non-interactive \
  --url "https://git.atris.site" \
  --token "ВСТАВЬТЕ_ТОКЕН" \
  --executor "docker" \
  --docker-image "eclipse-temurin:21-jdk" \
  --description "vm-v2-mini-docker" \
  --tag-list "docker,vps" \
  --docker-privileged="false" \
  --docker-volumes "/cache"
```

Проверка:

```bash
sudo gitlab-runner status
sudo gitlab-runner list
```

В UI: **Settings → CI/CD → Runners** — runner **зелёный / online**.

> Если регистрация интерактивная (старый способ):  
> `sudo gitlab-runner register` → URL → token → description → tags → executor `docker` → default image `eclipse-temurin:21-jdk`.

---

## Шаг 4. Первый pipeline (build + test)

1. Запушьте `.gitlab-ci.yml` в GitLab (ветка `main` или MR).
2. Проект → **Build → Pipelines**.
3. Должны пойти jobs `build` и `test`.

Локально проверить синтаксис нельзя без runner, но можно смотреть лог job в UI.

Если `build` падает:

| Ошибка | Что сделать |
|--------|-------------|
| `gradlew: not found` | добавить Gradle Wrapper (шаг 1b) |
| `no runners / stuck` | runner offline — `sudo gitlab-runner verify` |
| OOM / killed | мало RAM — см. шаг 7 |
| timeout | увеличить в job `timeout: 30m` |

---

## Шаг 5. CI/CD Variables (для deploy)

Проект → **Settings → CI/CD → Variables → Add variable**:

| Key | Value | Flags |
|-----|--------|--------|
| `DEPLOY_HOST` | `127.0.0.1` или IP VPS | Protected |
| `DEPLOY_USER` | `root` или `deploy` | Protected |
| `SSH_PRIVATE_KEY` | содержимое **приватного** ключа целиком (`-----BEGIN...`) | Masked + Protected |
| `DEPLOY_PATH` | путь к коду на сервере, напр. `/opt/blog-platform` | Protected |

**Protected** = переменные доступны только на protected branches (`main`).

### Ключ для деплоя

На VPS (или ПК):

```bash
ssh-keygen -t ed25519 -f ~/.ssh/gitlab_deploy -N ""
# публичный — в authorized_keys пользователя DEPLOY_USER на сервере
cat ~/.ssh/gitlab_deploy.pub >> ~/.ssh/authorized_keys
# приватный — в GitLab Variable SSH_PRIVATE_KEY
cat ~/.ssh/gitlab_deploy
```

Если GitLab и приложение на **одном** сервере, `DEPLOY_HOST=127.0.0.1` нормален: job в контейнере ходит по SSH на хост.

Проверьте, что на сервере код клонирован:

```bash
mkdir -p /opt/blog-platform
cd /opt/blog-platform
git clone git@git.atris.site:atris/products/blog-platform.git .
# или ваш реальный path группы
cp .env.example .env   # заполнить секреты один раз вручную
```

В `.gitlab-ci.yml` путь должен совпадать с `DEPLOY_PATH` (см. актуальный файл).

---

## Шаг 6. Docker и Deploy jobs

Они **manual**: в Pipeline нажмите ▶ на `docker:build` и/или `deploy:production`.

Для `docker:build` нужен Docker-in-Docker — на 8 GB RAM это тяжело.  
Практичный вариант для одного VPS:

- **не** жать `docker:build` в CI;
- в `deploy` делать на сервере: `git pull && docker compose up -d --build`.

Так и настроено в упрощённом пайплайне: deploy сам собирает на хосте.

---

## Шаг 7. Память (важно)

На сервере уже GitLab (~2–3 GiB) + blog-platform (~2–3 GiB).  
CI job ещё +1–2 GiB.

Рекомендации:

1. Swap 4 GB — уже есть.  
2. Не гонять CI и тяжёлый ручной rebuild одновременно.  
3. В runner ограничить concurrent:

```bash
sudo nano /etc/gitlab-runner/config.toml
```

```toml
concurrent = 1
```

```bash
sudo gitlab-runner restart
```

4. При OOM — временно останавливать ненужные UI-контейнеры на время pipeline.

---

## Шаг 8. Рабочий процесс команды

```text
Developer: feature-ветка → push → MR
           Pipeline: build + test должны быть зелёные

Maintainer: Approve + Merge в main
            Pipeline на main: build + test
            Вручную ▶ deploy:production (когда готовы выкатить)
```

В настройках проекта можно включить:

**Settings → Merge requests → Pipelines must succeed**  
— нельзя смержить MR с красным CI.

---

## Шаг 9. Проверка «всё работает»

Чеклист:

- [ ] Runner online  
- [ ] Push в MR → pipeline `build`/`test` зелёный  
- [ ] Merge в `main` → pipeline появился  
- [ ] Variables заданы  
- [ ] Manual `deploy:production` обновляет контейнеры  
- [ ] `curl https://api.atris.site/...` (или ваш health) отвечает  

Команды на сервере:

```bash
sudo gitlab-runner status
sudo gitlab-runner verify
docker ps | head
free -h
```

---

## Частые проблемы

| Симптом | Решение |
|---------|---------|
| Pending forever | нет runner / tags не совпадают |
| `permission denied (publickey)` на deploy | неверный `SSH_PRIVATE_KEY` или нет ключа в `authorized_keys` |
| Deploy тянет старый код | на сервере другой remote/ветка; проверьте `DEPLOY_PATH` |
| 502 во время CI | нехватка RAM — `concurrent=1`, не билдить всё сразу |
| Job в Docker не видит образы хоста | для same-server deploy используйте SSH+compose на хосте (как в текущем yml) |

---

## Файлы

| Файл | Назначение |
|------|------------|
| `.gitlab-ci.yml` | описание пайплайна |
| этот гайд | настройка Runner и Variables |
| `docs/GITLAB_SETUP_FULL.md` §10 | краткая версия в общем гайде |

После шагов 2–4 у вас уже будет автоматическая проверка каждого MR. Deploy оставите ручным — это безопаснее на одном VPS.
