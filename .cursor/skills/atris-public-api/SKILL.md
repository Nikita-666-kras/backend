---
name: atris-public-api
description: >-
  Implements or debugs ATRIS public catalog API usage (drones, parts, kits,
  categories, media). Use when fetching https://api.atris.site, fixing 404/CORS,
  mediaUrl, null prices, or page/content unwrapping.
---

# ATRIS public API

## Contract

- Base: `https://api.atris.site` (no trailing slash)
- Only GET (+ OPTIONS). No JWT.
- Envelope: `{ data: ... }`
- Page lists: `data.content`, `data.number`, `data.size`, `data.totalElements`, `data.totalPages`
- Categories: `data` is a **flat array**; build tree via `parentId`

## Endpoints

`/drones`, `/drones/{slug}`, `/part-categories`, `/parts`, `/parts/{sku}`, `/kits`, `/kits/{sku}`, `/media/{uuid}`

Never call `/parts/by-id/...` or mutating methods on public host.

## mediaUrl

```js
function mediaUrl(path) {
  if (!path) return "";
  if (/^https?:\/\//i.test(path)) return path;
  return "https://api.atris.site" + (path.charAt(0) === "/" ? path : "/" + path);
}
```

## price

`null` / missing / `<= 0` → show «По запросу».

## 404 diagnosis

If `/parts` 404 and `/auth` 401 → nginx points to admin `:8080`. Must be public `:8081`.

Details: `d:\Jobs\База знаний\knowledge\02-public-api-catalog.md` and `07-deploy-checklist.md`.
