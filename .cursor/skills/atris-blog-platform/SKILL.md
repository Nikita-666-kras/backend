---
name: atris-blog-platform
description: >-
  Works on ATRIS blog-platform backend (parts-service, post-service, dual
  gateway admin:8080 public:8081, docker, admin-ui). Use for Java services,
  nginx deploy, CORS env, or API contract changes.
---

# ATRIS blog-platform

## Root

`d:\Jobs\blog-platform\`

## Gateways

| Instance | Host port | Profile | Routes |
|----------|-----------|---------|--------|
| api-gateway | 8080 | admin | `/auth`, `/admin`, `/manager`, GET `/media` |
| public-gateway | 8081 | public | GET `/posts`, `/parts`, `/kits`, `/drones`, `/part-categories`, `/media/{uuid}` |

Production `api.atris.site` **must** proxy to **8081**.

## Parts domain

- Entities: Part, Kit (+ items), Drone, PartCategory (`parentId`)
- Public search forces `PUBLISHED`
- Part `price` nullable
- Kit has `priceMode` MANUAL|SUM and `items[]`

## When changing API

1. Update service + DTO
2. Ensure public-gateway routes still match (`application-public.yml` needs exact `/parts` and `/parts/**`)
3. Update KB `knowledge/02-public-api-catalog.md` and Atris `atris_parts_api_tilda_guide.md`
4. Recreate `public-gateway` if CORS env changed

## Docs

- `README.md`, `TILDA.md`
- `deploy/nginx/public-api.example.conf`
- KB: `01-architecture.md`, `07-deploy-checklist.md`
