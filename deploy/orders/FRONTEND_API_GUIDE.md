# Frontend guide: Orders API (for AI / T123 generation)

> **Audience:** LLM or developer generating Tilda T123 blocks (vanilla JS) for ATRIS site.  
> **Goal:** User collects cart in browser → submits order → backend creates amoCRM deal.  
> **Do not** call amoCRM from browser. **Do not** embed secrets in HTML.

---

## 1. API endpoint

| | |
|---|---|
| **Method** | `POST` |
| **Production URL** | `https://api.atris.site/public/orders` |
| **Local dev URL** | `http://localhost:8081/public/orders` |
| **Content-Type** | `application/json` |
| **Auth** | None by default. If server has `ORDERS_SECRET`, send header `X-Order-Secret: <value>` |

### Response envelope

Always `{ "data": { ... } }` on success.

```json
{
  "data": {
    "orderId": "ord_20260812143022_a1b2c3d4",
    "leadId": 47667377,
    "contactId": 64899569,
    "status": "accepted"
  }
}
```

HTTP **201 Created** on success.

### Error responses

| HTTP | Meaning |
|------|---------|
| 400 | Invalid body (phone, empty items, `consentPd` not true) |
| 401 | Wrong/missing `X-Order-Secret` |
| 405 | Wrong gateway (POST blocked — fix nginx / public-gateway) |
| 502 | amoCRM create failed |
| 503 | Orders disabled or CRM not configured |

Error body (Spring default):

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "invalid phone",
  "path": "/public/orders"
}
```

---

## 2. Request body schema

```typescript
interface CreateOrderRequest {
  name: string;          // required, max 120
  phone: string;         // required, min 10 digits after normalization
  email?: string;        // optional
  items: OrderItem[];    // required, min 1
  meta?: OrderMeta;
  consentPd: true;       // required, must be literal true
  consentMailing?: boolean;
}

interface OrderItem {
  sku: string;           // required — article / SKU
  partId?: string;       // optional UUID from catalog API
  title: string;         // required — display name
  qty?: number;          // default 1, min 1
  price?: number | null; // null or omit = "По запросу"
}

interface OrderMeta {
  source?: string;       // e.g. "parts", "store", "АТРИС — запчасти"
  pageUrl?: string;      // window.location.href
  utm?: {
    campaign?: string;
    referrer?: string;
  };
  yaCid?: string;        // Yandex Metrika client id if available
}
```

### Example payload

```json
{
  "name": "Иван Петров",
  "phone": "+7 (900) 123-45-67",
  "email": "ivan@example.com",
  "items": [
    {
      "sku": "DJI-123",
      "partId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "title": "Пропеллер",
      "qty": 2,
      "price": 1500
    },
    {
      "sku": "HD580-456",
      "title": "Мотор ESC",
      "qty": 1,
      "price": null
    }
  ],
  "meta": {
    "source": "parts",
    "pageUrl": "https://atris.su/parts",
    "utm": {
      "campaign": "1/мета/клики/поиск/тест",
      "referrer": "https://atris.su/parts"
    },
    "yaCid": "1234567890123456789"
  },
  "consentPd": true,
  "consentMailing": true
}
```

---

## 3. Cart on frontend (existing ATRIS pattern)

On `/parts` cart already exists as `window.ATRIS_PARTS_CART` (localStorage key `atris_parts_cart_v1`).

### Cart item shape (from catalog)

```javascript
{
  id: "uuid-or-empty",
  sku: "DJI-123",
  title: "Пропеллер",
  price: 1500,      // or null
  image: "/media/...",
  qty: 2
}
```

Read cart:

```javascript
var cart = window.ATRIS_PARTS_CART.get();
var items = cart.items || [];
```

Map to API items:

```javascript
function cartItemsForApi(items) {
  return (items || []).map(function (item) {
    return {
      sku: item.sku,
      partId: item.id || undefined,
      title: item.title,
      qty: item.qty || 1,
      price: item.price == null ? null : Number(item.price)
    };
  });
}
```

---

## 4. Submit function (copy-paste base)

Use in T123 `<script>` inside checkout form handler. Vanilla JS only.

```javascript
(function () {
  var API = (window.ATRIS_PARTS && window.ATRIS_PARTS.apiBase) || "https://api.atris.site";
  var ORDER_URL = API.replace(/\/$/, "") + "/public/orders";

  function readUtm() {
    try {
      var params = new URLSearchParams(location.search);
      return {
        campaign: params.get("utm_campaign") || "",
        referrer: document.referrer || ""
      };
    } catch (e) {
      return {};
    }
  }

  function normalizePhone(value) {
    return String(value || "").replace(/\D/g, "");
  }

  window.ATRIS_SUBMIT_ORDER = function (opts) {
    opts = opts || {};
    var cartApi = window.ATRIS_PARTS_CART;
    var cart = cartApi ? cartApi.get() : { items: [] };
    var items = cart.items || [];

    if (!items.length) {
      return Promise.reject(new Error("empty_cart"));
    }
    if (normalizePhone(opts.phone).length < 10) {
      return Promise.reject(new Error("invalid_phone"));
    }
    if (!opts.consentPd) {
      return Promise.reject(new Error("consent_required"));
    }

    var payload = {
      name: String(opts.name || "").trim(),
      phone: String(opts.phone || "").trim(),
      email: opts.email ? String(opts.email).trim() : undefined,
      items: items.map(function (item) {
        return {
          sku: item.sku,
          partId: item.id || undefined,
          title: item.title,
          qty: item.qty || 1,
          price: item.price == null ? null : Number(item.price)
        };
      }),
      meta: {
        source: opts.source || "parts",
        pageUrl: location.href,
        utm: readUtm(),
        yaCid: typeof window.ATRIS_GET_YA_CID === "function" ? window.ATRIS_GET_YA_CID() : undefined
      },
      consentPd: true,
      consentMailing: !!opts.consentMailing
    };

    var headers = { "Content-Type": "application/json", Accept: "application/json" };
    if (window.ATRIS_ORDER_SECRET) {
      headers["X-Order-Secret"] = window.ATRIS_ORDER_SECRET;
    }

    return fetch(ORDER_URL, {
      method: "POST",
      headers: headers,
      body: JSON.stringify(payload)
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (body) {
        if (!res.ok) {
          var err = new Error(body.message || "order_failed");
          err.status = res.status;
          throw err;
        }
        if (cartApi && typeof cartApi.clear === "function") {
          cartApi.clear();
        }
        if (window.ATRIS_PARTS_CART_UI && typeof window.ATRIS_PARTS_CART_UI.render === "function") {
          window.ATRIS_PARTS_CART_UI.render();
        }
        return body.data || body;
      });
    });
  };
})();
```

---

## 5. Checkout UX flow

```
[Корзина] → [Оформить заявку] → [Форма: имя, телефон, согласия]
     → submit → loading на кнопке
     → POST /public/orders
     → success: очистить корзину, показать «Заявка принята»
     → error: показать текст, корзину не очищать
```

### UI states (required)

| State | Behavior |
|-------|----------|
| **idle** | Submit enabled when phone valid + consents checked + cart not empty |
| **loading** | Submit disabled, text «Отправляем…» |
| **success** | Hide form or show thank-you panel, clear cart |
| **error** | Message «Не удалось отправить, попробуйте позже или позвоните» |

### Form fields

| Field | HTML | Validation |
|-------|------|------------|
| Имя | `input type="text"` | optional but recommended |
| Телефон | `input type="tel"` | required, ≥10 digits |
| Email | `input type="email"` | optional |
| Согласие ПДн | `checkbox name="consent_pd"` | required checked |
| Рассылка | `checkbox name="consent_mailing"` | optional |

Wire submit (example):

```javascript
submitBtn.addEventListener("click", function () {
  submitBtn.disabled = true;
  window.ATRIS_SUBMIT_ORDER({
    name: nameInput.value,
    phone: phoneInput.value,
    consentPd: consentPd.checked,
    consentMailing: consentMailing.checked,
    source: "АТРИС — заказ запчастей"
  })
  .then(function (data) {
    showSuccess(data.orderId);
  })
  .catch(function (err) {
    showError(err.message);
  })
  .finally(function () {
    submitBtn.disabled = false;
  });
});
```

---

## 6. Integration with existing popup form

Current flow uses Tilda Form bridge (`atris_popup_form.html`) and `ATRIS_PARTS_ORDER_SOURCE()`.

**Migration path:**

1. Keep popup UI (phone, name, consents).
2. On submit **replace** Tilda form POST with `ATRIS_SUBMIT_ORDER(...)`.
3. Remove dependency on hidden Tilda form for parts checkout (optional fallback).

Do **not** send cart text in `source` field anymore — server builds note from `items[]`.

---

## 7. T123 / Tilda constraints

Follow `.cursorrules` / ATRIS T123 rules:

1. All CSS in `<style>`, all JS in `<script>` — no npm, no React.
2. Wrap block in `.uc-atris-…` or `#recXXXX` marker.
3. Do not use global `body`, `a` selectors without marker.
4. Mobile: full-width submit, touch targets ≥44px.
5. CORS: site origin must be in `PUBLIC_CORS_ALLOWED_ORIGINS` on server.

---

## 8. What backend does (for context, not frontend code)

1. Validates request
2. Finds/creates amoCRM **contact** by phone
3. Sets AR (last 4 digits) on contact field `1902113`
4. Creates **lead** with tags `tilda`, `запчасти`
5. Writes order lines into lead **note**
6. Fills UTM custom fields if provided

Frontend does **not** need amoCRM field IDs.

---

## 9. Testing checklist

- [ ] Empty cart → error before fetch
- [ ] Valid order → 201, cart cleared
- [ ] Deal appears in amoCRM with note
- [ ] Tags `tilda` + `запчасти` on deal (for Salesbot UTM bots)
- [ ] Mobile checkout works
- [ ] CORS: no browser block on `atris.su`
- [ ] Double-click submit does not create duplicate (disable button while loading)

---

## 10. Config on site (optional)

In `00_config.html` or order block:

```javascript
window.ATRIS_PARTS = window.ATRIS_PARTS || {};
window.ATRIS_PARTS.apiBase = "https://api.atris.site";
// only if ORDERS_SECRET set on server:
// window.ATRIS_ORDER_SECRET = "...";  // avoid in public HTML if possible
```

Prefer **no secret in HTML**. Use server-side rate limit instead.

---

## 11. curl for manual test

```bash
curl -s -X POST https://api.atris.site/public/orders \
  -H "Content-Type: application/json" \
  -d '{"name":"Тест","phone":"+79001234567","items":[{"sku":"T-1","title":"Test","qty":1,"price":100}],"meta":{"source":"parts"},"consentPd":true}'
```

Backend setup: [`README.md`](README.md)
