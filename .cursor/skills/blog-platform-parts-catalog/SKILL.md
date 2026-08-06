---
name: blog-platform-parts-catalog
description: >-
  Parts-service domain: parts, kits, drones, categories tree, nullable price,
  catalogFilter, CSV/Excel import, admin vs public paths, media UUIDs. Use when
  changing parts-service, import, catalog filters, kits, or admin parts UI.
---

# Parts catalog

Service: `parts-service` (:9006), DB Flyway `V2__nullable_part_price.sql`.

## Surfaces

| Audience | Paths |
|----------|--------|
| Public (:8081) | `/parts`, `/kits`, `/drones`, `/part-categories` |
| Admin (:8080 BFF) | `/admin/parts|kits|drones|part-categories` + import |

## Domain

- Categories: flat list + **`parentId`** (null = root); not bound to drone
- Parts: unique `sku`, **`price` nullable**, link `droneId` + `categoryId`
- Kits: `priceMode` `MANUAL|SUM`, `items[]` with `partSku`, `qty`, `partPrice`
- Status: `DRAFT` | `PUBLISHED` | `ARCHIVED`

## catalogFilter

`NO_PRICE` | `NO_NAME` | `NO_PHOTO` | `NO_DRONE` | `NO_CATEGORY` | `INCOMPLETE`  
(JPQL uses string enum keys.)

## Import (admin)

`/admin/parts/import/template.csv|preview|apply`  
Columns: `sku,name,price,drone,category,kit_sku,description,external_id`. Empty price → null. Default apply status DRAFT.

## Media

IDs stored on parts; blobs in **post-service** volume. Responses expose relative `/media/{uuid}`. Watermark via post-service processing + `watermarks/watermark.png`.

## Script

`scripts/import-t50-kits.mjs` — kit bulk import against **admin** base URL (`/auth` + `/admin`), not public API.
