---
name: blog-platform-overview
description: >-
  Blog-platform ATRIS monorepo architecture: Spring services, dual gateway
  (8080 admin / 8081 public), ports, UIs, mental model. Use when working in
  blog-platform, atris, parts catalog platform, or when the user asks what the
  project is / where services live / which port to use.
---

# Blog-platform overview

Canonical code: `d:\blog-platform` or `d:\Jobs\blog-platform`.  
Full KB: `d:\Jobs\База знаний\` (prefer live `docker-compose.yml` + `api-gateway/` over old TILDA docs).

## Mental model

- **Read-only published content** (Tilda, storefront, public blog) → **public-gateway `:8081`** / `https://api.atris.site`
- **Login + CRUD** (admin-ui, manager-ui) → **api-gateway `:8080`** (usually via UI proxy on 8088/8090)

## Ports

| What | Port |
|------|------|
| Admin gateway | 8080 |
| Public gateway | 8081 |
| Admin UI | 8088 |
| Public UI | 8089 |
| Manager UI | 8090 |
| SSO / post / admin / parts / proposal / logging | 9001 / 9003 / 9005 / 9006 / 9007 / 9008 |

## Modules

`sso-service`, `article-service` (Gradle `:post-service`), `admin-service`, `parts-service`, `proposal-service`, `logging-service`, `api-gateway`, `admin-ui`, `manager-ui`, `public-ui`, `common-library`.

## Agent rules

1. Do not put catalog public routes on admin gateway or vice versa.
2. If docs say public API on `:8080`, they are **outdated** — use `:8081`.
3. Deeper topics: read sibling skills `blog-platform-gateway`, `blog-platform-public-api`, `blog-platform-parts-catalog`, `blog-platform-deploy`, or KB files under `d:\Jobs\База знаний\`.
