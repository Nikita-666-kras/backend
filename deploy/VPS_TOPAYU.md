# topayu.ru → blog-platform API для atris.su

## Диагностика (сейчас)

Проверка снаружи показала:

| URL | Результат |
|-----|-----------|
| `http://topayu.ru/` | дефолтная страница nginx «Welcome to nginx!» |
| `http://topayu.ru/posts` | 404 |
| `https://topayu.ru/posts` | обрыв соединения (ERR_CONNECTION_CLOSED) |
| `:8080` снаружи | недоступен |

**Вывод:** gateway не проксируется через nginx, HTTPS не настроен для API.

Браузер на `https://atris.su` не может загрузить `https://topayu.ru/posts` → ошибка в Tilda.

---

## Что сделать на VPS (по шагам)

### 1. Убедиться, что API работает локально

```bash
cd /path/to/blog-platform
docker compose up -d
curl http://127.0.0.1:8080/application/health
curl "http://127.0.0.1:8080/posts?page=0&size=3"
```

Должен быть JSON с `"data"`.

### 2. CORS для atris.su

В `.env` на VPS:

```env
CORS_ALLOWED_ORIGINS=https://atris.su,https://www.atris.su,http://localhost:8088
```

Перезапуск:

```bash
docker compose up -d --force-recreate api-gateway
```

### 3. Nginx reverse proxy

```bash
sudo cp deploy/nginx/topayu.ru.conf /etc/nginx/sites-available/topayu.ru
sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -sf /etc/nginx/sites-available/topayu.ru /etc/nginx/sites-enabled/topayu.ru
```

**Сначала только HTTP** (для certbot), временно закомментируйте блок `server { listen 443 ...}` и редирект в блоке 80, оставьте:

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
curl "http://topayu.ru/posts?page=0&size=1"
```

### 4. SSL (Let's Encrypt)

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d topayu.ru -d www.topayu.ru
```

Включите полный конфиг из `deploy/nginx/topayu.ru.conf` с HTTPS.

```bash
sudo nginx -t && sudo systemctl reload nginx
curl "https://topayu.ru/posts?page=0&size=1"
curl "https://topayu.ru/application/health"
```

### 5. Firewall

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
# 8080 наружу НЕ открывать — только через nginx
```

---

## Tilda (atris.su)

В `00_config.html` на страницах `/blog` и `/blog/post`:

```javascript
window.ATRIS_BLOG = {
  api: "https://topayu.ru",
  postPath: "/blog/post",
  pageSize: 9
};
```

---

## Проверка после настройки

1. `https://topayu.ru/application/health` → 200
2. `https://topayu.ru/posts?page=0&size=3` → JSON с постами
3. DevTools на atris.su → Network → запрос к topayu.ru без CORS/SSL ошибок
4. Карточки блога отображаются

---

## Частые ошибки

| Ошибка | Причина |
|--------|---------|
| ERR_CONNECTION_CLOSED | HTTPS не настроен или сломан SSL на topayu.ru |
| 404 nginx | Нет proxy_pass на :8080 |
| 403 из браузера | Нет atris.su в CORS_ALLOWED_ORIGINS |
| Mixed content | API только по http, сайт по https |
| Пустая лента | Нет опубликованных постов в admin-ui |
