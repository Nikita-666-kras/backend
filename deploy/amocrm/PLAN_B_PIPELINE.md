# План B — работает без Salesbot widget_request

Salesbot `widget_request` у вас **не вызывает** внешний URL (в nginx нет POST от amo).  
**План B** — триgger Digital Pipeline **«API: отправить webhook»** + наш сервер через **amo API** сам ставит АР и переименовывает сделку.

Автоназвание через Salesbot **можно убрать** — имя сделки тоже выставит сервер.

---

## Шаг 1 — токен amoCRM (один раз)

1. amoCRM → **Настройки → Интеграции → Создать интеграцию** (приватная).
2. Вкладка **Ключи и доступы** → скопируйте **долгосрочный токен** (access token).
3. Запомните субдомен, например `atris` → base URL: `https://atris.amocrm.ru`  
   (или ваш домен `.kommo.com`).

Права: контакты и сделки — **чтение и запись**.

---

## Шаг 2 — переменные на VPS

В `/opt/blog-platform/.env` (или `docker-compose` environment):

```ini
AMOCRM_BASE_URL=https://ВАШ_СУБДОМЕН.amocrm.ru
AMOCRM_ACCESS_TOKEN=долгосрочный_токен
AMOCRM_PIPELINE_ENABLED=true
AMOCRM_PHONE_FIELD_ID=1844509
AMOCRM_AR_FIELD_ID=1902731
```

Деплой:

```bash
cd /opt/blog-platform
git pull origin main
docker compose build --no-cache integrations-service
docker compose up -d --force-recreate integrations-service
```

---

## Шаг 3 — триgger в amo (Digital Pipeline)

1. **Сделки → Настроить воронку → этап «В РАБОТЕ»**.
2. **+ Добавить триgger** (не Salesbot).
3. Выберите **«API: отправить webhook»** / **«Отправить webhook»**.
4. URL:

```text
https://api.atris.site/amocrm/pipeline-autoar
```

5. **Сохранить воронку**.

**Удалите** (или отключите):
- отдельный триgger **«АвтоАР»** Salesbot;
- объединённый JSON с `widget_request` — он ломает сценарий.

Salesbot «Автоназвание» **не обязателен** — сервер сам переименует сделку в формате  
`Ар 0440 Имя Компания ...`

---

## Шаг 4 — тест

```bash
# имитация webhook от amo
curl -s -X POST https://api.atris.site/amocrm/pipeline-autoar \
  -H 'Content-Type: application/json' \
  -d '{"leads":{"status":[{"id":ID_ВАШЕЙ_СДЕЛКИ}]}}'

docker compose logs --tail=20 integrations-service | grep pipeline-autoar
```

Ожидание:

```text
pipeline-autoar: lead_id=... contact_id=... ar=0440 ar_field_id=1902731 lead_patch=true name=...
```

Потом **переведите сделку** на этап «В РАБОТЕ» — в логе должна быть та же строка.

---

## Если не работает

| Симптом | Что проверить |
|---------|----------------|
| `pipeline-autoar: disabled` | `AMOCRM_PIPELINE_ENABLED=true` |
| `amo api: not configured` | `AMOCRM_BASE_URL` + `AMOCRM_ACCESS_TOKEN` |
| `phone empty` | у контакта сделки телефон в поле **1844509** |
| `no contact` | к сделке привязан контакт |
| `amo api GET failed: 401` | токен просрочен / неверный |
| POST в nginx нет | триgger «API webhook», не Salesbot; URL точный |

---

## Почему Salesbot не работал

1. Два Salesbot на одном этапе — amo блокирует второй.
2. `widget_request` в редакторе кода **часто не шлёт** POST (в логах только curl).
3. План B **не использует Salesbot** для АР — только webhook + API.

Файл автоназвания (если нужен отдельно): [`autonaming-only.json`](autonaming-only.json)
