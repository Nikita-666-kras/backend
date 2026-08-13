# Blog Platform

Расширяемая микросервисная платформа блога:

- `post-service` — публичные посты + медиа
- `parts-service` — каталог запчастей / комплектов / дронов
- `sso-service` — login / refresh / roles
- `admin-service` — BFF для админки
- `integrations-service` — **хаб внешних интеграций** (amoCRM, заказы; см. `deploy/integrations/ARCHITECTURE.md`)
- `api-gateway` — **admin-gateway** (JWT + CRUD): `/auth`, `/admin`, `/manager`, preview `/media`
- `public-gateway` — **read-only API** (GET) + **POST `/amocrm/**`**: `/posts`, `/parts`, `/kits`, `/drones`, `/media/{uuid}`
- `admin-ui` / `manager-ui` / `public-ui`

## Архитектура

```text
Admin UI (:8088) ──proxy──┐
Manager UI (:8090) ───────┼──► api-gateway :8080   (profile=admin)
                          │      ├── /auth/**     → sso
                          │      ├── /admin/**    → admin-service
                          │      ├── /manager/**  → proposal-service
                          │      └── GET /media/** → post-service
                          │
Public UI / Tilda / amo ──┴──► public-gateway :8081 (profile=public)
                                 ├── GET /posts/** /media/{uuid} → post-service
                                 ├── GET /parts|/kits|/drones/** → parts-service
                                 ├── POST /public/orders → integrations-service (корзина → CRM)
                                 └── POST /amocrm/** → integrations-service (Salesbot / Pipeline)
                                      (status forced PUBLISHED on catalog)
```

Gateway — одна кодовая база (`api-gateway`), два инстанса с разными профилями:
`SPRING_PROFILES_ACTIVE=admin|public|combined` + `GATEWAY_MODE=…`.

## Security model

- Наружу: `:8080` (admin API), `:8081` (public GET API + amoCRM POST), UI порты
- Public-gateway отклоняет не-GET (`405`), кроме **`POST /amocrm/**`**; пути `/admin|/auth|/manager` → `404`
- Admin-gateway не отдаёт публичный каталог `/parts|/kits|/posts` (только через `/admin/**`)
- SSO / DB порты **не** публикуются на host
- Gateway снимает client `X-User-*`, выставляет их только после JWT (admin)
- Публичный `GET` — только `PUBLISHED`

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

- Admin gateway: http://localhost:8080
- Public gateway (GET): http://localhost:8081
- Admin UI: http://localhost:8088
- Public blog UI: http://localhost:8089

Health:

```bash
curl http://localhost:8080/application/health
curl http://localhost:8081/application/health
# public rejects writes (except /amocrm):
curl -X POST http://localhost:8081/parts -i   # → 405
curl -X POST http://localhost:8081/amocrm/autoar -H 'Content-Type: application/json' \
  -d '{"data":{"phone":"+7 (999) 123-45-67","ar_field_id":"1902113"}}'   # → 200
```

Salesbot JSON: `deploy/amocrm/autoar.json` → URL `https://api.atris.site/amocrm/autoar`.

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

Пошаговый гайд: [TILDA.md](TILDA.md) — публичный `GET /posts`, картинки `GET /media/{id}`, CORS, HTML-блоки.

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
  article-service/   # Gradle alias: post-service (+ media files)
  sso-service/
  admin-service/
  api-gateway/
  admin-ui/
  public-ui/         # публичная лента блога (Vue 3)
  docker-compose.yml
  .env.example
```

Медиа (локальный volume `media-data`):

- Админ: `POST/GET/DELETE /admin/media`
- Публично: `GET /media/{id}`
- В постах: `coverMediaId`, `coverUrl`, `media[]`