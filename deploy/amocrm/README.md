# amoCRM Salesbot — autoar (АР из телефона)

Обработчик: `POST https://api.atris.site/amocrm/autoar`  
Сервис: `integrations-service` → public-gateway `:8081`.

1. Сервер считает АР = 4 последние цифры телефона.  
2. `POST` на `return_url` (корень JSON от amo): `{ "data": { "ar": "0440" } }`.  
3. Второй блок Salesbot пишет поле контакта из `{{json.ar}}`.

Сервер **не** обновляет контакт через amo API.

---

## 1. VPS

```bash
cd /opt/blog-platform
git pull origin master
docker compose up -d --build integrations-service public-gateway
```

`api.atris.site` → `proxy_pass http://127.0.0.1:8081` (не 8080).

```bash
curl -s -X POST https://api.atris.site/amocrm/autoar \
  -H 'Content-Type: application/json' \
  -d '{"data":{"phone":"+7 (999) 123-45-67","ar_field_id":"1853459"}}'
# → {"ok":true,"ar":"4567","status":"success"}
# (без return_url — только проверка вычисления; WARN missing return_url — норма для curl)
```

---

## 2. Env (опционально)

```env
AMOCRM_AR_FIELD_ID=1902113
# AMOCRM_WEBHOOK_SECRET=...   # тогда URL: .../autoar?secret=...
```

---

Если бот «не работает» — см. [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## 3. amoCRM — Salesbot

Загрузите / вставьте [`autoar.json`](autoar.json):

1. Шаг `widget_request` → наш URL + `contact_id`, `phone`.
2. Шаг `set_custom_fields` → значение `{{json.ar}}`, поле `1902113`.

Проверьте ID телефона: `{{contact.cf.1844509}}` — замените, если в аккаунте другой.

**Важно:** бот стартует при переходе сделки на этап с ботом, не от смены телефона.

После запуска в логе:

```text
autoar: contact_id=<id> ar=0440 status=success return_url=present
autoar: continue ok ar=0440
```

```bash
docker compose logs -f integrations-service | grep autoar
```

---

## 4. Контракт запроса от amo

```json
{
  "token": "...",
  "return_url": "https://....amocrm.ru/.../continue/...",
  "data": {
    "contact_id": "123",
    "phone": "+7...",
    "ar_field_id": "1853459"
  }
}
```

`return_url` — **в корне**, не в `data`. В Java: `@JsonProperty("return_url")`.

Continue от сервера:

```json
{ "data": { "ar": "0440" } }
```

(+ `Authorization: Bearer <token>` если amo прислал token).
