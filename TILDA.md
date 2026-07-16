# Подключение блога к Тильде

Админка (`:8088`) — для редакторов. Тильда читает **публичный API** через gateway (`:8080`) без логина.

```text
Тильда (HTML-блок / Zero Block)
        │  GET /posts , GET /posts/{slug}
        ▼
   api-gateway :8080
        ▼
   post-service  → только PUBLISHED
```

## 1. Что нужно на VPS

1. Поднять платформу: `docker compose up --build -d`
2. Открыть наружу **только** gateway (или reverse-proxy на него), например `https://api.yourdomain.ru` → `:8080`
3. Админку держать отдельно: `https://admin.yourdomain.ru` → `:8088` (или VPN)

Публичные эндпоинты (без токена):

| Метод | URL | Назначение |
|-------|-----|------------|
| `GET` | `/posts?page=0&size=10` | лента опубликованных |
| `GET` | `/posts/{slug}` | один пост по slug |

Ответ обёрнут в `{ "data": ... }`. У поста важны поля:

- `title`, `slug`, `shortDescription`
- `htmlContent` — готовый HTML (безопаснее для вставки)
- `content` — исходный Markdown
- `tags`, `categories`, `createdAt`, `readingTime`

Проверка:

```bash
curl "https://api.yourdomain.ru/posts?page=0&size=5"
curl "https://api.yourdomain.ru/posts/your-post-slug"
```

## 2. CORS для Тильды

Браузер на сайте Тильды шлёт `Origin` с вашего домена. Добавьте его в `.env`:

```env
CORS_ALLOWED_ORIGINS=http://localhost:8088,https://yourproject.tilda.ws,https://www.yourdomain.ru
```

Перезапуск gateway:

```bash
docker compose up -d --force-recreate api-gateway
```

Без вашего Origin запросы из HTML-блока Тильды получат **403**.

## 3. Вариант A — лента новостей (HTML-блок)

На странице Тильды: **Блок → Другое → HTML-код**. Вставьте скрипт, подставив свой API:

```html
<div id="blog-feed">Загрузка…</div>
<script>
(function () {
  var API = 'https://api.yourdomain.ru'; // ваш gateway
  var root = document.getElementById('blog-feed');

  fetch(API + '/posts?page=0&size=6')
    .then(function (r) { return r.json(); })
    .then(function (body) {
      var posts = (body.data && body.data.content) || [];
      if (!posts.length) {
        root.textContent = 'Пока нет публикаций';
        return;
      }
      root.innerHTML = posts.map(function (p) {
        var href = '/news/' + encodeURIComponent(p.slug); // страница поста на Тильде
        return (
          '<article style="margin:0 0 1.5rem">' +
            '<h3 style="margin:0 0 .35rem"><a href="' + href + '">' + escapeHtml(p.title) + '</a></h3>' +
            '<p style="margin:0;opacity:.75">' + escapeHtml(p.shortDescription || '') + '</p>' +
          '</article>'
        );
      }).join('');
    })
    .catch(function () {
      root.textContent = 'Не удалось загрузить новости';
    });

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
})();
</script>
```

Стили подгоните под макет Тильды (или оберните в классы Zero Block).

## 4. Вариант B — страница одной новости

1. Создайте страницу, например `/news` (или `/news/one`).
2. В HTML-блоке читайте `slug` из query или из path:

```html
<article id="blog-post">Загрузка…</article>
<script>
(function () {
  var API = 'https://api.yourdomain.ru';
  var root = document.getElementById('blog-post');

  // ?slug=my-post  или  /news/my-post
  var params = new URLSearchParams(location.search);
  var slug = params.get('slug');
  if (!slug) {
    var parts = location.pathname.replace(/\/+$/, '').split('/');
    slug = parts[parts.length - 1];
    if (slug === 'news' || slug === 'one') slug = null;
  }

  if (!slug) {
    root.textContent = 'Не указан slug поста';
    return;
  }

  fetch(API + '/posts/' + encodeURIComponent(slug))
    .then(function (r) {
      if (!r.ok) throw new Error('not found');
      return r.json();
    })
    .then(function (body) {
      var p = body.data;
      root.innerHTML =
        '<h1>' + escapeHtml(p.title) + '</h1>' +
        '<p style="opacity:.7">' + escapeHtml(p.shortDescription || '') + '</p>' +
        '<div class="post-body">' + (p.htmlContent || '') + '</div>';
    })
    .catch(function () {
      root.textContent = 'Пост не найден';
    });

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
})();
</script>
```

В ленте ссылки вида `/news/?slug=hello-world-123` или настройте ЧПУ Тильды под `/news/{slug}`.

> `htmlContent` уже санитизирован на бэкенде, но не вставляйте сырой `content` (Markdown) через `innerHTML` без конвертации.

## 5. Workflow редакции

1. Редактор открывает админку → создаёт пост → **Опубликовать**
2. Пост сразу появляется в `GET /posts`
3. Тильда подтягивает его при открытии страницы (или по таймеру / кнопке «Обновить»)

Черновики и архив **не** видны публичному API.

## 6. HTTPS и домен

- На VPS: Nginx/Caddy с Let's Encrypt на `api.yourdomain.ru`
- В Тильде в скриптах только `https://…` (mixed content блокируется)
- В `CORS_ALLOWED_ORIGINS` — точный Origin сайта (`https://www.…`, без слэша в конце)

Пример Nginx:

```nginx
server {
  listen 443 ssl;
  server_name api.yourdomain.ru;
  # ssl_certificate ...

  location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Real-IP $remote_addr;
  }
}
```

## 7. Частые ошибки

| Симптом | Причина |
|---------|---------|
| 403 на `GET /posts` из браузера | Origin Тильды нет в `CORS_ALLOWED_ORIGINS` |
| Пустая лента | Нет постов со статусом «Опубликован» |
| 404 на `/posts/{slug}` | Неверный slug или пост не опубликован |
| Mixed content | На HTTPS-сайте вызываете `http://…` API |
| Работает в curl, нет в Тильде | curl не проверяет CORS — смотрите Network → preflight OPTIONS |

## 8. Безопасность

- В HTML Тильды **не** кладите JWT, пароли и `INTERNAL_API_KEY`
- Публичный API только читает опубликованные посты
- Админку и `:8088` не светите без необходимости; лучше отдельный поддомен + ограничение доступа
