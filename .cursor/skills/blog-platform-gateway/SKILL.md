---
name: blog-platform-gateway
description: >-
  Dual Spring Cloud Gateway for blog-platform: admin profile :8080 vs public
  :8081, routes, PublicReadOnlyFilter 405, nginx api.atris.site, CORS. Use when
  debugging 404 on /parts, wrong gateway, nginx proxy_pass, CORS 403, or
  changing application-admin.yml / application-public.yml.
---

# Blog-platform gateway

## Split

| Instance | Host | Profile | Allows |
|----------|------|---------|--------|
| `api-gateway` | 8080 | admin | `/auth`, `/admin`, `/manager`, GET `/media` |
| `public-gateway` | 8081 | public | GET `/posts`, `/media`, `/parts`, `/kits`, `/drones`, `/part-categories` |

Code: `api-gateway/src/main/resources/application-{admin,public,combined}.yml`  
Filter: `PublicReadOnlyFilter` — non-GET → **405**; admin surfaces on public → **404**.
**Exception:** `POST|OPTIONS /amocrm/**` → `integrations-service` (amoCRM Salesbot autoar).

**Do not** add `Method=GET,HEAD,OPTIONS` predicates on public routes — unmatched methods become **404** before the filter (should be 405).

Nginx: if edge rejects non-GET, add `location /amocrm` **before** that guard (`deploy/nginx/public-api.example.conf`).

## Diagnose api.atris.site

| Check | Meaning |
|-------|---------|
| `/application/health` 200 | gateway up |
| `/auth` or `/admin` → 401 | **admin** profile (wrong for public host) |
| `/parts` → 404 | nginx likely points to **8080** |

Fix nginx: `proxy_pass http://127.0.0.1:8081;`  
Example: `deploy/nginx/public-api.example.conf`

## CORS

Public: `PUBLIC_CORS_ALLOWED_ORIGINS` (exact origins, no trailing slash).  
Then: `docker compose up -d --force-recreate public-gateway`.

## Verify

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8081/parts
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://127.0.0.1:8081/parts
# expect 200 and 405
```
