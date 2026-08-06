---
name: atris-overview
description: >-
  Orients the agent in the ATRIS Jobs monorepo (Atris Tilda frontend +
  blog-platform backend + knowledge base). Use when starting work on ATRIS,
  asking where files live, or choosing which skill/doc to open.
---

# ATRIS overview

## Before coding

1. Read `d:\Jobs\База знаний\INDEX.md`
2. Pick domain:
   - Public catalog API → skill `atris-public-api` + `knowledge/02-public-api-catalog.md`
   - Parts Tilda page → `atris-parts-tilda`
   - Any T123 block → `atris-tilda-blocks` + `atris-design-system`
   - Backend/docker/gateway → `atris-blog-platform`

## Roots

- Frontend: `d:\Jobs\Atris\`
- Backend: `d:\Jobs\blog-platform\`
- Knowledge: `d:\Jobs\База знаний\`

## Hard rules

- Do not invent API paths; use documented public GET API only.
- If `api.atris.site/parts` is 404 but `/auth` works → fix nginx to `:8081`, not the frontend.
- Tilda blocks: self-contained, Manrope, section spacing, engineering look.
