# Гайд с нуля: АвтоАР в amoCRM → api.atris.site

Полные рекомендации по доке: [BEST_PRACTICES.md](BEST_PRACTICES.md) · ревью: [CODE_REVIEW.md](CODE_REVIEW.md) · чеклист: [LAUNCH_CHECKLIST.md](LAUNCH_CHECKLIST.md)

Цель: при **переходе сделки на этап** Salesbot считает 4 последние цифры телефона и пишет в поле **АР** (`1902113`).

```text
Перенос сделки на этап
  → Salesbot «АвтоАР»
  → POST https://api.atris.site/amocrm/autoar
  → сервер POST return_url: { "data": { "ar": "0440", "status": "success" } }
  → если status=success → set_custom_fields {{json.ar}}
```

Официально: [widget_request](https://www.amocrm.ru/developers/content/digital_pipeline/salesbot), [continue](https://www.amocrm.ru/developers/content/crm_platform/widgets-api).

---

## Часть A. Сервер

```bash
cd /opt/blog-platform
git pull origin master
docker compose build --no-cache integrations-service
docker compose up -d --force-recreate integrations-service public-gateway

# api.atris.site → 127.0.0.1:8081
grep proxy_pass /etc/nginx/sites-enabled/api.atris.site

curl -s -X POST https://api.atris.site/amocrm/autoar \
  -H 'Content-Type: application/json' \
  -d '{"data":{"contact_id":"test","phone":"+7 (918) 958-04-40"},"return_url":"https://httpbin.org/post"}'
# → {"ok":true,"ar":"0440","status":"success"}
```

---

## Часть B. Код бота в amo

Настройки → Salesbot → **АвтоАР** → режим кода → вставить [`autoar.json`](autoar.json) → **Сохранить**.

Ключевые моменты JSON:
1. `widget_request` + `goto` на шаг 1 (паттерн из доки виджета).
2. Шаг 1: `conditions` по `{{json.status}}` = `success` → `set_custom_fields`.
3. Телефон: `{{contact.cf.1844509}}` — замените ID при необходимости (`CFV[id]` в HTML).
4. АР: `custom_fields_id` **1902113**, value `{{json.ar}}`.

Показать на сервере:

```bash
cat /opt/blog-platform/deploy/amocrm/autoar.json
```

---

## Часть C. Триггер этапа

| Поле | Значение |
|------|----------|
| Salesbot | АвтоАР |
| **Выполнить** | **После перехода или создания в этапе** |
| Применить ко всем | **выкл** |

Сохранить воронку.

---

## Часть D. Тест

```bash
docker compose logs -f integrations-service | grep autoar
```

Перетащить сделку **с другого этапа** на этап с АвтоАР.

Успех:
- история: **АвтоАР**
- `sudo grep '/amocrm/autoar' /var/log/nginx/access.log | tail -3` → свежий **POST** (не curl)
- лог: `continue ok ar=… status=success`
- поле АР = 4 цифры

---

## Часть E. Типичные затыки

| Симптом | Причина |
|---------|---------|
| Нет POST в nginx | Триггер «только создание» / бот не на этапе |
| POST есть, нет continue ok | Сеть до amo / token |
| continue ok, поле пустое | Не тот маркер → проверьте `{{json.ar}}`; или status=fail |
| phone_len=0 | Неверный CF телефона |

Не смотреть HAR браузера и JsSIP — webhook с серверов amo.
