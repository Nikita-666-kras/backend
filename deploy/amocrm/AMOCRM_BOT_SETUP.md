# AutoAR Salesbot — по официальным примерам amoCRM

**Источники (проверено 2026-08-10):**

| Пример | URL |
|--------|-----|
| `widget_request` + continue | [Salesbot / widget_request](https://www.amocrm.ru/developers/content/digital_pipeline/salesbot) |
| Паттерн виджета: request → goto → conditions | [Интеграция виджета в Salesbot](https://www.amocrm.ru/developers/content/integrations/salesbot_widget) |
| Continue API | [Widgets API / continue](https://www.amocrm.ru/developers/content/crm_platform/widgets-api) |
| Kommo (тот же формат) | [private-chatbot-integration](https://developers.kommo.com/docs/private-chatbot-integration) |

---

## Почему «АвтоАР» не запускался

В [доке amoCRM](https://www.amocrm.ru/developers/content/digital_pipeline/salesbot):

> *«Вы не сможете продолжить выполнения бота, если уже будет запущен другой бот по сущности.»*

На этапе «В РАБОТЕ» одновременно срабатывают **два триггера**:

1. **Автоназвание** — занимает сделку и отрабатывает.
2. **АвтоАР** — amo **не запускает** (в истории его нет, POST в nginx нет).

**Решение:** один бот на этап — либо только AutoAR, либо **объединить** AutoAR с «Автоназванием» в одном JSON.

---

## Официальный паттерн (как в доке виджета)

```text
Шаг 0 (question):
  widget_request → POST на ваш URL
  goto → step 1

Шаг 1 (question):
  conditions: {{json.status}} = success
    → set_custom_fields ({{json.ar}})
    → exits success
  exits fail   ← обязательная ветка «иначе», как в примере amo
```

Файл: [`autoar-official.json`](autoar-official.json) — копия паттерна из доки.

Сервер должен:

1. Принять POST с `return_url` и `token` в корне.
2. **Синхронно** POST на `return_url`: `{ "data": { "ar": "0440", "status": "success" } }` + `Authorization: Bearer {token}`.
3. Ответить amo **200** за ~2 сек.

Код: `integrations-service` → `AutoArService`.

---

## Вариант A — только AutoAR (тест)

1. На этапе «В РАБОТЕ» **удалите** триггер «Автоназвание» (временно).
2. Оставьте **только** «АвтоАР».
3. В боте вставьте **`autoar-official.json`** → **Сохранить**.
4. Сохраните воронку → переведите сделку с другого этапа.

Ожидание: POST в nginx + `continue ok` в логах.

---

## Объединённый бот «Автоназвание + АР»

Готовый JSON: [`autonaming-plus-autoar.json`](autonaming-plus-autoar.json)

Порядок шагов:
1. `widget_request` → сервер считает АР из телефона
2. `goto` → шаг 1
3. если `{{json.status}}` = success:
   - записать `{{json.ar}}` в поле сделки **1902731** (type 2)
   - автоназвание сделки (в шаблоне **`{{json.ar}}`**, не `{{lead.cf.1902731}}` — поле ещё не обновлено в том же тике)

На этапе оставить **один** тригger — «Автоназвание». Триgger «АвтоАР» **удалить**.

---

## Вариант B — название + АР в одном боте (ручная сборка)

1. **Salesbot → Автоназвание сделки → Показать код**.
2. Скопируйте все `handler` из **первого** блока `"question"` (шаг автоназвания).
3. **Сразу после них** (в том же массиве `question`, до закрывающей `]`) вставьте блок из [`autoar-snippet.json`](autoar-snippet.json):

```json
{
  "handler": "widget_request",
  "params": {
    "url": "https://api.atris.site/amocrm/autoar",
    "data": {
      "lead_id": "{{lead.id}}",
      "contact_id": "{{contact.id}}",
      "phone": "{{contact.cf.1844509}}",
      "ar_field_id": "1902731"
    }
  }
},
{
  "handler": "goto",
  "params": {
    "type": "question",
    "step": 1
  }
}
```

4. **Добавьте второй элемент массива** (шаг 1) — целиком второй `{ "question": [ ... ] }` из `autoar-official.json` (conditions + set_custom_fields + exits).
5. **Сохранить** бота.
6. На этапе **удалите отдельный триггер «АвтоАР»** — оставьте один триггер «Автоназвание».
7. Сохраните воронку → тест переводом сделки.

---

## Вариант C — запуск второго бота из первого (дока)

В конце бота «Автоназвание» можно добавить action `salesbot-start` с `bot_id` бота AutoAR — но проще **Вариант B** (один JSON, один тригger).

---

## Поля

| ID | Назначение |
|----|------------|
| `1844509` | Телефон контакта (`{{contact.cf.1844509}}`) |
| `1902731` | Поле АР на сделке (`set_custom_fields`, type 2 = сделка) |

---

## Проверка

```bash
sudo tail -f /var/log/nginx/access.log | grep '/amocrm/autoar'
docker compose logs -f integrations-service | grep autoar
```

Успех:

```text
autoar: contact_id=<число> ... return_url=present token=present
autoar: continue ok ar=0440
```

---

## Файлы

| Файл | Назначение |
|------|------------|
| `autoar-official.json` | Standalone бот по доке amo |
| `autoar-snippet.json` | Фрагмент для вставки в «Автоназвание» |
| `autoar.json` | То же, что official (основной) |
