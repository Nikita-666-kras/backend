# GitLab на atris.site — пошаговая установка

Цель: свой GitLab на `**https://git.atris.site**`, общий доступ к проектам, слияние в `**main` только через Merge Request с одобрением администратора**.

**Требования к VPS:** Ubuntu 22.04/24.04, **≥4 GB RAM** (лучше 8 GB), **≥20 GB** свободного диска, Docker.

Файлы в репозитории:


| Файл                               | Назначение          |
| ---------------------------------- | ------------------- |
| `deploy/gitlab/docker-compose.yml` | GitLab CE           |
| `deploy/nginx/git.atris.site.conf` | Nginx reverse proxy |


---

## Шаг 0. DNS

У регистратора домена `atris.site` создайте запись:


| Тип | Имя   | Значение      |
| --- | ----- | ------------- |
| A   | `git` | IP вашего VPS |


Проверка (с вашего ПК или сервера):

```bash
dig +short git.atris.site
# или
nslookup git.atris.site
```

Должен вернуться IP VPS. Пока DNS не указывает на сервер — HTTPS (certbot) не заработает.

Откройте в firewall порты **80**, **443** и **2222** (SSH-clone GitLab):

```bash
# ufw (если используете)
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 2222/tcp
sudo ufw status
```

Порт **22** оставьте для обычного SSH на сервер; Git-клон пойдёт на **2222**.

---

## Шаг 1. Docker (если ещё нет)

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
| sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker
docker --version
docker compose version
```

---

## Шаг 2. Запуск GitLab

Скопируйте compose на сервер (или клонируйте этот репозиторий) и запустите:

```bash
sudo mkdir -p /srv/gitlab
# если репозиторий уже на сервере:
sudo cp /path/to/blog-platform/deploy/gitlab/docker-compose.yml /srv/gitlab/docker-compose.yml

cd /srv/gitlab
sudo docker compose up -d
```

Первый старт занимает **5–15 минут**. Смотрите логи:

```bash
sudo docker compose logs -f gitlab
# Ctrl+C когда увидите, что Unicorn/Puma и nginx поднялись
```

Проверка локально:

```bash
curl -sI http://127.0.0.1:8929 | head -5
```

Пароль пользователя `**root**` (действует ~24 часа после первого старта):

```bash
sudo docker exec -it gitlab grep 'Password:' /etc/gitlab/initial_root_password
```

Сохраните пароль. После входа сразу смените его.

---

## Шаг 3. Nginx + HTTPS

### 3.1. Установите nginx и certbot (если нет)

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
sudo mkdir -p /var/www/certbot
```

### 3.2. Конфиг сайта

```bash
sudo cp /path/to/blog-platform/deploy/nginx/git.atris.site.conf \
  /etc/nginx/sites-available/git.atris.site
sudo ln -sf /etc/nginx/sites-available/git.atris.site /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

Проверка по HTTP:

```bash
curl -sI http://git.atris.site | head -10
```

Должен ответить GitLab (не «Welcome to nginx»).

### 3.3. SSL

```bash
sudo certbot --nginx -d git.atris.site
```

Следуйте мастеру (email, согласие). Certbot допишет HTTPS и редирект.

Проверка:

```bash
curl -sI https://git.atris.site | head -10
```

Откройте в браузере: **[https://git.atris.site](https://git.atris.site)** → войдите как `root`.

---

## Шаг 4. Базовая настройка GitLab (UI)

1. Смените пароль `root`: **Avatar → Edit profile → Password**.
2. (Опционально) отключите публичную регистрацию:
  **Admin Area (гаечный ключ) → Settings → General → Sign-up restrictions** → снимите **Sign-up enabled** → Save.  
   Пользователей будете создавать вы.
3. Создайте пользователей: **Admin Area → Users → New user** (Developer / Maintainer).

---

## Шаг 5. Группа и общие проекты

1. **Menu → Groups → New group**
  - Name: например `atris`  
  - Visibility: **Private** (или Internal)
2. **Group → Manage → Members** — добавьте людей:
  - **Developer** — пишут код, создают MR
  - **Maintainer** / **Owner** — админы, одобряют слияния в `main`
3. Новые репозитории создавайте **внутри группы** (`New project` → в группе `atris`).

---

## Шаг 6. Защита `main` + одобрение  

Делайте на уровне **группы** (наследуется всеми проектами) или в каждом проекте.

### 6.1. Protected branch (группа или проект)

**Settings → Repository → Protected branches** → Protect a branch:


| Параметр                  | Значение                                               |
| ------------------------- | ------------------------------------------------------ |
| Branch                    | `main` (или `master`)                                  |
| Allowed to merge          | **Maintainers**                                        |
| Allowed to push and merge | **No one** (или только Maintainers — лучше **No one**) |
| Allowed to force push     | **Off**                                                |


Итог: напрямую в `main` никто не пушит — только через Merge Request.

### 6.2. Обязательное одобрение MR

**Settings → Merge requests → Merge request approvals**:

1. **Add approval rule** (или Edit):
  - Rule name: `Admins`
  - Approvals required: **1**
  - Approvers: пользователи-Maintainer / группа админов
2. Включите:
  - **Prevent approval by author** — автор MR не может одобрить сам себя
3. Save

### 6.3. Рекомендуемые опции MR

В **Settings → Merge requests**:

- Squash commits — по желанию
- Delete source branch when merge request is accepted — удобно включить
- Merge method: Merge commit или Fast-forward — по вкусу команды

---

## Шаг 7. Рабочий процесс команды

```text
Developer:
  git checkout -b feature/login
  … коммиты …
  git push -u origin feature/login
  → в GitLab: Create merge request → target: main

Admin (Maintainer):
  Review → Approve → Merge
```

Клон по HTTPS:

```bash
git clone https://git.atris.site/atris/my-project.git
```

Клон по SSH (порт **2222**):

```bash
# ~/.ssh/config
Host git.atris.site
  HostName git.atris.site
  Port 2222
  User git

git clone git@git.atris.site:atris/my-project.git
```

SSH-ключ добавьте в GitLab: **Preferences → SSH Keys**.

---

## Шаг 8. Перенос существующего проекта (пример blog-platform)

```bash
cd /path/to/blog-platform
git remote rename origin old-origin   # если был GitHub/другой remote
# или:
git remote add gitlab https://git.atris.site/atris/blog-platform.git
git push -u gitlab --all
git push gitlab --tags
```

После пуша в GitLab проверьте, что `main` protected и approvals включены.

---

## Обслуживание

```bash
cd /srv/gitlab

# статус
sudo docker compose ps

# логи
sudo docker compose logs -f --tail=100 gitlab

# перезапуск
sudo docker compose restart

# обновление GitLab (бэкап сначала!)
sudo docker compose pull
sudo docker compose up -d
```

Бэкап (упрощённо):

```bash
sudo docker exec -t gitlab gitlab-backup create
# архивы: /srv/gitlab/data/backups/
```

---

## Типичные проблемы


| Симптом                           | Что проверить                                                      |
| --------------------------------- | ------------------------------------------------------------------ |
| Certbot: connection refused / DNS | A-запись `git` → IP VPS, порты 80/443                              |
| 502 Bad Gateway                   | `docker compose ps`, логи GitLab ещё стартует (подождите 5–15 мин) |
| Мало RAM / OOM                    | ≥4 GB; временно отключите другие тяжёлые сервисы                   |
| Clone по SSH не работает          | Порт **2222**, `~/.ssh/config` с `Port 2222`                       |
| Пуш в `main` отклонён             | Так и должно быть — создайте MR                                    |


---

## Чеклист готовности

- `https://git.atris.site` открывается, логин `root` работает, пароль сменён
- Публичная регистрация выключена
- Группа создана, участники добавлены
- `main` — Protected, push = No one
- MR approvals: ≥1 от Maintainer, author не одобряет себя
- Тестовый MR: Developer → Approve → Merge проходит только после Approve

