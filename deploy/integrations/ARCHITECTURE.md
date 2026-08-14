# integrations-service — хаб внешних интеграций

Порт **9009** · Docker: `integrations-service` · Gateway: public `:8081` / admin `:8080`

## Зачем отдельный сервис

Вся **внешняя** связь сайта с миром — в одном месте:

- секреты (amoCRM token и т.п.) только на сервере;
- фронт (Tilda) шлёт простой JSON, не знает про CRM;
- новый канал (почта, Slack, 1С) = новый класс, без правок parts/post.

**Не плодить** отдельные микросервисы под каждый канал — один `integrations-service` с модулями внутри.

## Что уже живёт здесь

| Endpoint | Направление | Назначение |
|----------|-------------|------------|
| `POST /amocrm/autoar` | amoCRM → сервер → amoCRM continue | Salesbot AutoAR |
| `POST /amocrm/pipeline-autoar` | Digital Pipeline → сервер → amo API | Plan B AutoAR |
| `POST /public/orders` | Сайт → сервер → amoCRM (+ MAX, если настроен) | Заказ из корзины |

## Архитектура внутри

```
api/                    REST-контроллеры (тонкие)
  OrderController
  AutoArController
  PipelineAutoArController

domain/                 общие модели событий
  OrderContext

channel/                исходящие интеграции
  OrderNotifier         интерфейс (MAX, позже Telegram/email)
  amocrm/
    AmoCrmOrderChannel  обязательный канал для заказов
  max/
    MaxOrderNotifier    личные сообщения менеджерам (user_id)

service/                оркестрация + amo webhook-логика
  OrderOrchestrator     validate → CRM → notifiers
  AutoArService
  PipelineAutoArService
  AmoCrmApiClient       HTTP-клиент amo API v4
```

### Поток заказа

```
POST /public/orders
  → OrderOrchestrator
      1. validate (phone, items, consentPd)
      2. AmoCrmOrderChannel.push()     ← обязательно, иначе 502
      3. OrderNotifier × N             ← MAX, если MAX_BOT_TOKEN задан
  → 200 { orderId, leadId, contactId }
```

Если MAX не настроен или упал — заказ в amoCRM всё равно создаётся.

## Gateway

Public gateway пропускает POST только на:

- `/amocrm/**`
- `/public/orders`

Остальное — read-only GET (каталог, блог).

Маршруты: `application-public.yml` → `integrations-service:9009`

## Env

```ini
# amoCRM (обязательно для заказов)
AMOCRM_BASE_URL=https://oooatris.amocrm.ru
AMOCRM_ACCESS_TOKEN=...
AMOCRM_AR_FIELD_ID=1902113
AMOCRM_PHONE_FIELD_ID=1844509

# Заказы
ORDERS_ENABLED=true

# MAX (опционально): личка менеджерам по user_id
# TLS: platform-api2.max.ru подписан Минцифры — клиент грузит CA из classpath /certs
# MAX_BOT_TOKEN=...
# MAX_ORDERS_USER_IDS=290387676

# CORS для Tilda
PUBLIC_CORS_ALLOWED_ORIGINS=https://atris.su,https://www.atris.su
```

## Как добавить новый канал

1. Создать `channel/email/EmailOrderNotifier implements OrderNotifier`
2. `enabled()` — проверка env
3. `notify(OrderContext ctx)` — отправка
4. Spring подхватит `@Component` автоматически

Пример приоритета: `order()` — 10 Telegram, 20 Email.

## Что НЕ класть сюда

| Задача | Куда |
|--------|------|
| Каталог запчастей, цены | `parts-service` |
| Статьи блога | `post-service` |
| КП / PDF | `proposal-service` |
| Логин пользователей | `sso-service` |

## Документация

- Заказы / фронт: [`orders/FRONTEND_API_GUIDE.md`](orders/FRONTEND_API_GUIDE.md)
- amoCRM боты: [`amocrm/`](../amocrm/)

## Деплой

```bash
docker compose build integrations-service public-gateway
docker compose up -d --force-recreate integrations-service public-gateway
```
