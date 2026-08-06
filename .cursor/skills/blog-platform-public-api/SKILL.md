---
name: blog-platform-public-api
description: >-
  Public catalog and blog API for Tilda/atris_parts frontends: base URL
  api.atris.site, envelope {data}, pageable parts/kits/drones, category parentId
  tree, nullable price, mediaUrl helper. Use when integrating frontend, Tilda
  blocks, atris_parts_api_blocks, or documenting public endpoints.
---

# Public API (frontend)

Base: `https://api.atris.site` — GET only, no JWT. Envelope: `{ "data": ... }`.

## Endpoints

- `GET /drones?page&size&q` → page `{ content, totalElements, number, size }`
- `GET /drones/{slug}`
- `GET /part-categories` → **array** (build tree via `parentId`)
- `GET /parts?droneId&categoryId&q&page&size`
- `GET /parts/{sku}`
- `GET /kits?droneId&q&page&size`
- `GET /kits/{sku}`
- `GET /media/{uuid}` — binary; JSON fields use `"/media/{uuid}"`

Only **PUBLISHED** visible. Ignore `status` query on public.

## Frontend helpers

```js
const API = 'https://api.atris.site';
const mediaUrl = (p) => !p ? null : (p.startsWith('http') ? p : API + p);
const items = body.data.content;      // lists
const cats = body.data;               // categories array
// part.price may be null → show "по запросу"
```

## Part fields (key)

`id`, `name`, `sku`, `price` (nullable), `currency`, `droneId`, `droneName`, `categoryId`, `categoryName`, `coverUrl`, `mediaUrls`, `sortOrder`.

## Server prerequisites

1. Nginx `api.*` → `:8081` (not 8080)
2. `PUBLIC_CORS_ALLOWED_ORIGINS` includes site Origin (`https://atris.su`, …)

Do not change frontend API host if nginx is wrong — fix proxy first.
