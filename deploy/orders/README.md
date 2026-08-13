# Orders API — backend setup

Endpoint: `POST https://api.atris.site/public/orders`

Service: `integrations-service` → amoCRM API v4 (contact + lead + note + tags).

## Env (`.env` on VPS)

```ini
AMOCRM_BASE_URL=https://oooatris.amocrm.ru
AMOCRM_ACCESS_TOKEN=...
AMOCRM_AR_FIELD_ID=1902113
AMOCRM_PHONE_FIELD_ID=1844509

ORDERS_ENABLED=true
# optional anti-spam shared key (header X-Order-Secret)
# ORDERS_SECRET=

# optional: place leads on specific stage (0 = amo default)
# AMOCRM_ORDERS_PIPELINE_ID=
# AMOCRM_ORDERS_STATUS_ID=

AMOCRM_UTM_CAMPAIGN_FIELD_ID=1844521
AMOCRM_UTM_REFERRER_FIELD_ID=1844527
ORDERS_TAG_PARTS=запчасти
ORDERS_TAG_SITE=tilda
```

Public gateway CORS must include Tilda origins:

```ini
PUBLIC_CORS_ALLOWED_ORIGINS=https://atris.su,https://www.atris.su
```

## Deploy

```bash
cd /opt/blog-platform
git pull
docker compose build integrations-service public-gateway
docker compose up -d --force-recreate integrations-service public-gateway
```

## Smoke test

```bash
curl -s -X POST http://127.0.0.1:8081/public/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "name":"Тест API",
    "phone":"+79001234567",
    "items":[{"sku":"TEST-1","title":"Тестовая позиция","qty":1,"price":100}],
    "meta":{"source":"parts","pageUrl":"https://atris.su/parts"},
    "consentPd":true
  }'
```

Expected: `201` + `{"data":{"orderId":"ord_...","leadId":...,"contactId":...,"status":"accepted"}}`

Logs: `docker compose logs --tail=20 integrations-service | grep orders`

## Frontend contract

See [`FRONTEND_API_GUIDE.md`](FRONTEND_API_GUIDE.md) — instructions for AI / T123 blocks.
