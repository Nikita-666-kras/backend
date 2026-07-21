# Public blog UI

Публичная страница блога, которая читает **опубликованные** посты из API gateway.

```text
public-ui (:8089)
   ├── GET /posts
   ├── GET /posts/{slug}
   └── GET /media/{id}
        ▼
   api-gateway (:8080)
```

## Запуск локально

1. Поднимите backend:

```bash
docker compose up --build -d
```

2. Запустите UI:

```bash
cd public-ui
npm install
npm run dev
```

Откройте http://localhost:5174

Vite проксирует `/posts` и `/media` на gateway `:8080`.

## Docker

```bash
docker compose up --build -d public-ui
```

Откройте http://localhost:8089

## Маршруты

| URL | Экран |
|-----|-------|
| `/` | лента опубликованных статей |
| `/post/{slug}` | страница одной статьи |

## Переменные

| Переменная | Назначение |
|------------|------------|
| `VITE_SITE_NAME` | название блога в шапке и title |
| `PUBLIC_SITE_NAME` | build-arg для Docker |

## Workflow

1. Редактор публикует пост в admin-ui (`:8088`)
2. Пост появляется в `GET /posts`
3. Public UI показывает его сразу после обновления страницы

## Подключение к Tilda / atris.su

Если нужен блог **внутри Tilda**, используйте [TILDA.md](../TILDA.md) — HTML-блоки с fetch на gateway.

Если нужен отдельный поддомен, например `https://blog.atris.su`:

1. Проксируйте его на `:8089`
2. Добавьте Origin в `CORS_ALLOWED_ORIGINS`, если Tilda/другой фронт будет ходить в API напрямую

## Проверка API

```bash
curl "http://localhost:8080/posts?page=0&size=5"
curl "http://localhost:8080/posts/your-slug"
```
