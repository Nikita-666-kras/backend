# Как правильно писать Salesbot + внешний webhook (по доке amoCRM)

**Checked:** 2026-08-10  
Источники:
- [Salesbot / widget_request](https://www.amocrm.ru/developers/content/digital_pipeline/salesbot)
- [Continue API (widgets)](https://www.amocrm.ru/developers/content/crm_platform/widgets-api)
- [Виджет в Salesbot](https://www.amocrm.ru/developers/content/integrations/salesbot_widget)
- Skill: `amocrm-integrations`

---

## 1. Выбрать механизм

| Задача | Механизм |
|--------|----------|
| Считать что-то на своём сервере и вернуть в бота | **Salesbot `widget_request`** + `return_url` |
| UI-блок в конструкторе бота | Приватный виджет + `salesbot_designer` |
| Просто записать поле без сервера | Только `action` / `set_custom_fields` в боте |
| Слушать события CRM | Webhooks Digital Pipeline / API webhooks |

**АвтоАР** = первый вариант (не zip-виджет).

---

## 2. Контракт `widget_request` (официально)

### Исходящий запрос amo → ваш URL

```json
{
  "token": "JWT_TOKEN",
  "data": { "...то что передали в params.data..." },
  "return_url": "https://subdomain.amocrm.ru/api/v4/salesbot/{botId}/continue/{continueId}"
}
```

Правила:
- Ответить **HTTP 200 за ~2 секунды** (ack).
- Бот **не продолжит**, пока не получит POST на `return_url`.
- `return_url` — **в корне**, не в `data`.
- JWT подписан секретом интеграции (валидация — опционально).

### Continue → amo

```json
{
  "data": {
    "ar": "0440",
    "status": "success"
  }
}
```

- Ключи из `data` доступны дальше как **`{{json.ar}}`**, **`{{json.status}}`**.
- Заголовок: `Authorization: Bearer <token>` из входящего запроса.
- **`execute_handlers`** на continue: официально только **`show`** и **`goto`**.  
  **Нельзя** через continue ставить `set_custom_fields` — только следующий шаг бота.

---

## 3. Паттерн кода бота (рекомендуемый)

Как в примере виджета amo: `widget_request` + `goto`, затем шаг с условием по `{{json.status}}`.

```text
Шаг 0: widget_request → ваш сервер
        goto → шаг 1
Шаг 1: если {{json.status}} = success
          → set_custom_fields (сделка, {{json.ar}}, type 2)
```

Файл: [`autoar.json`](autoar.json).

`set_custom_fields`:
- `type: 2` = сделка (поле АР **1902731**), `type: 1` = контакт ([дока](https://www.amocrm.ru/developers/content/digital_pipeline/salesbot))
- `custom_fields_id` = int id поля
- `value` = строка или маркер (`{{json.ar}}`)

---

## 4. Паттерн сервера (рекомендуемый)

1. Распарсить `return_url` / `token` / `data` (`@JsonProperty("return_url")` в Java).
2. Посчитать результат быстро.
3. **POST `return_url`** с `{ "data": { "ar", "status" } }` (таймауты &lt; 1.5–2 с).
4. Ответить клиенту (amo) **200**.
5. Не обновлять контакт API v4 с сервера, если это делает шаг бота.

Код: `integrations-service` → `AutoArService`.

---

## 5. Digital Pipeline

- Триггер: **«После перехода или создания в этапе»**, если тест — перетаскиванием сделки.
- «Только после создания» → перенос **не** запустит бота.
- Галочка «применить ко всем» — не для отладки webhook.
- Проверка: история сделки + `nginx access.log` (`POST`, не curl) + лог сервиса `continue ok`.

Webhook идёт **с серверов amo**, не из браузера (HAR/DevTools бесполезны).

---

## 6. Чего избегать

| Ошибка | Почему |
|--------|--------|
| Ждать поля из HTTP 200 ответа widget_request | Данные для бота — только через `return_url` → `{{json.*}}` |
| `execute_handlers` + `set_custom_fields` | Не поддерживается на continue |
| Async continue «потом» без ожидания | Риск пустого `{{json.*}}` / гонки |
| Триггер «только создание» при тесте переносом | Бот не стартует |
| Смотреть JsSIP / HAR | Другой канал |

---

## 7. Чеклист нового бота «с сервером»

1. Endpoint публичный HTTPS, POST, 200 &lt; 2 с.  
2. DTO: `return_url` в корне.  
3. Continue: `{ data: { ... } }` + Bearer token.  
4. Бот: widget_request → (goto) → action по `{{json.*}}`.  
5. Триггер: переход **или** создание.  
6. Тест: перенос сделки → nginx POST → `continue ok` → поле в карточке.  
7. Документация: URL доки, права, ID полей (skill requirement).
