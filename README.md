# Blog Platform

Расширяемая микросервисная платформа блога:

- `post-service` — публичные посты
- `sso-service` — login / refresh / roles
- `admin-service` — BFF для админки
- `api-gateway` — единая точка входа + JWT
- `admin-ui` — Vue 3 админка

## Архитектура

```text
Admin UI (:8088) ──proxy──┐
                          ▼
                    api-gateway :8080   ← единственный публичный API
                      ├── /auth/**   → sso-service (internal)
                      ├── /admin/**  → admin-service (internal)
                      └── /posts/**  → post-service (internal)
                                           ▲
                                           │ X-Internal-Api-Key
                                    admin-service
```

## Security model

- Наружу опубликованы только `:8080` (gateway) и `:8088` (admin-ui)
- SSO / admin / post / DB порты **не** публикуются на host
- Gateway всегда снимает client `X-User-*`, выставляет их только после JWT
- Mutating `/posts` и `/admin` требуют роль `EDITOR` или `ADMIN`
- Публичный `GET /posts` — только `PUBLISHED` (slug тоже)
- `post-service` write/`by-id` только с `X-Internal-Api-Key`
- Refresh tokens хранятся как SHA-256 hash
- Seed users только при `APP_SEED_USERS=true`
- Admin UI: DOMPurify, sessionStorage, CSP, same-origin proxy

## Роли

- `EDITOR` — создавать, редактировать, publish/archive
- `ADMIN` — всё то же + удаление постов + создание пользователей

Demo users (только если `APP_SEED_USERS=true`):

- `admin / Admin123!`
- `editor / Editor123!`

## Запуск

```bash
cd blog-platform
cp .env.example .env   # задайте свои секреты
docker compose up --build -d
```

URLs:

- Gateway: http://localhost:8080
- Admin UI: http://localhost:8088

Health:

```bash
curl http://localhost:8080/application/health
```

## Admin UI

1. Открой http://localhost:8088
2. Войди как `admin` / `Admin123!`
3. Создай пост → Save draft / Publish
4. Users (только ADMIN) — создать editor/admin

Локальная разработка UI:

```bash
cd admin-ui
npm install
npm run dev
```

Vite proxy ходит на gateway `:8080`.

## Тильда

Пошаговый гайд: [TILDA.md](TILDA.md) — публичный `GET /posts`, CORS, HTML-блоки ленты и страницы поста.

## Postman через Gateway

Base URL: `http://localhost:8080`

### 1. Login

`POST /auth/login`

```json
{
  "username": "admin",
  "password": "Admin123!"
}
```

Скопируй `data.accessToken`.

### 2. Me

`GET /auth/me`  
Header: `Authorization: Bearer <token>`

### 3. Create draft

`POST /admin/posts`  
Header: `Authorization: Bearer <token>`

```json
{
  "title": "Hello from admin",
  "shortDescription": "Short",
  "content": "# Hello\nPublished via admin BFF",
  "tags": ["news"],
  "categories": ["blog"],
  "mediaObjectNames": []
}
```

### 4. Publish

`POST /admin/posts/{id}/publish`  
Header: `Authorization: Bearer <token>`

### 5. Public read

`GET /posts/{slug}` — без токена (только PUBLISHED)

### 6. Delete (ADMIN only)

`DELETE /admin/posts/{id}`  
Header: `Authorization: Bearer <token>`

## Добавление новых микросервисов

1. Новый Gradle-модуль + Dockerfile
2. `include(...)` в `settings.gradle.kts`
3. Сервис + (при необходимости) БД в `docker-compose.yml` **без** host ports
4. Route в `api-gateway`
5. При необходимости экран во Vue admin-ui

## Модули

```text
blog-platform/
  common-library/
  article-service/   # Gradle alias: post-service
  sso-service/
  admin-service/
  api-gateway/
  admin-ui/
  docker-compose.yml
  .env.example
```
