# Чеклист внедрения и запуска AutoAR

Связанные файлы: [CODE_REVIEW.md](CODE_REVIEW.md) · [SETUP_FROM_SCRATCH.md](SETUP_FROM_SCRATCH.md) · [autoar.json](autoar.json) · [TROUBLESHOOTING.md](TROUBLESHOOTING.md)

Официально: [Salesbot widget_request](https://www.amocrm.ru/developers/content/digital_pipeline/salesbot).

---

## Этап 0. Понять продукт (5 мин)

- [ ] Это **не** zip-виджет в карточке. Это **Salesbot + внешний webhook**.
- [ ] Сервер только считает АР и делает `return_url`.
- [ ] Поле АР пишет **второй шаг бота** (`{{json.ar}}`).

---

## Этап 1. Backend (VPS)

```bash
cd /opt/blog-platform
git pull origin master
grep -n JsonProperty integrations-service/src/main/java/com/blog/platform/integrations/api/dto/AmoDtos.java
# ожидается: @JsonProperty("return_url")

docker compose build --no-cache integrations-service
docker compose up -d --force-recreate integrations-service public-gateway
```

- [ ] `api.atris.site` → `proxy_pass http://127.0.0.1:8081`
- [ ] Smoke:

```bash
curl -s -X POST https://api.atris.site/amocrm/autoar \
  -H 'Content-Type: application/json' \
  -d '{"data":{"contact_id":"smoke","phone":"+7 (918) 958-04-40"},"return_url":"https://httpbin.org/post"}'
# {"ok":true,"ar":"0440","status":"success"}
```

- [ ] Лог: `return_url=present` (continue на httpbin может дать 503 — не страшно)

Опционально в `.env`:

```env
AMOCRM_AR_FIELD_ID=1902731
# AMOCRM_WEBHOOK_SECRET=...
```

---

## Этап 2. Salesbot в amoCRM

### 2.1 Создать / обновить бота

1. Настройки → Salesbot → бот **АвтоАР** (или создать).
2. Режим **кода / JSON**.
3. Вставить содержимое [autoar.json](autoar.json) целиком.
4. Проверить ID телефона: `{{contact.cf.1844509}}` → свой `CFV[id]` если другой.
5. Поле АР: `1902731` (сделка), value `{{json.ar}}`.
6. **Сохранить** бота.

### 2.2 Digital Pipeline (критично)

1. Воронка → настройки этапа (или Digital Pipeline).
2. Триггер **Запуск Salesbot** → бот **АвтоАР**.
3. **Выполнить:** **«После перехода или создания в этапе»**.
4. Галочку «применить ко всем сделкам» — **не** использовать для теста.
5. Сохранить воронку.

Рядом может быть «Автоназвание» — ок. Нужен **отдельный** триггер АвтоАР.

---

## Этап 3. Запуск (тест)

1. На VPS:

```bash
docker compose logs -f integrations-service | grep autoar
```

2. В amo: сделка с контактом, **телефон заполнен**.
3. Перетащить сделку **с другого этапа** → на этап с АвтоАР.
4. История сделки: есть **АвтоАР** (не только автоназвание).

### Успех

```bash
sudo grep '/amocrm/autoar' /var/log/nginx/access.log | tail -3
# свежий POST, User-Agent не curl/Chrome
```

```text
autoar: contact_id=<число> phone_len>0 ar=XXXX return_url=present token=present
autoar: continue ok ar=XXXX
```

Карточка сделки → поле АР = **4 цифры**.

---

## Этап 4. Диагностика (если тихо)

| Проверка | Команда / место | Затуп |
|----------|-----------------|--------|
| Бот в истории? | Карточка сделки | Триггер / этап |
| POST в nginx? | `grep autoar access.log` | amo не вызывает URL |
| Строка в docker? | `logs … \| grep autoar` | gateway / сервис |
| `continue ok`? | docker log | сеть до amo / token |
| Поле пустое при ok? | карточка | `{{json.data.ar}}` вместо `{{json.ar}}` |

**Не смотреть:** DevTools HAR, JsSIP Sipuni — webhook идёт с серверов amo.

---

## Этап 5. Прод

- [ ] Триггер только на нужных этапах (не на всём пайплайне без необходимости).
- [ ] Телефон обязателен до перехода (или бот пишет пустой АР / fail).
- [ ] Документация для команды: этот чеклист + SETUP_FROM_SCRATCH.
- [ ] При смене ID полей — обновить `autoar.json` и `AMOCRM_AR_FIELD_ID`.

---

## Documentation (skill requirement)

| Item | Value |
|------|--------|
| Official | https://www.amocrm.ru/developers/content/digital_pipeline/salesbot |
| Access | Public HTTPS POST, no OAuth for widget_request |
| Permissions | Salesbot on pipeline; lead custom field 1902731 |
| Handler | `https://api.atris.site/amocrm/autoar` |
| Code | `integrations-service`, gateway public profile |
| Skill | `amocrm-integrations` + `references/atris-autoar.md` |
