---
name: blog-platform-deploy
description: >-
  Deploy blog-platform on VPS with Docker Compose and nginx: rebuild gateways,
  point api.atris.site to 8081, PUBLIC_CORS, health checks. Use when deploying,
  recreating public-gateway, fixing production nginx, or VPS ops for atris.
---

# Deploy blog-platform

## Standard update

```bash
cd /opt/blog-platform   # or local path
git pull
docker compose build api-gateway public-gateway   # and others as needed
docker compose up -d --force-recreate api-gateway public-gateway
sudo nginx -t && sudo systemctl reload nginx
```

Firewall: **80/443 only** — do not expose 8080/8081/DB.

## Public API host

`api.atris.site` → `proxy_pass http://127.0.0.1:8081;`  
Template: `deploy/nginx/public-api.example.conf`  
Admin UI stays on `:8088` → internal admin gateway `:8080`.

## Env after public split

```env
PUBLIC_CORS_ALLOWED_ORIGINS=https://atris.su,https://www.atris.su,https://atris.site,https://www.atris.site
```

Recreate `public-gateway` after CORS changes.

## Smoke

```bash
curl -s https://api.atris.site/application/health
curl -s "https://api.atris.site/parts?page=0&size=1"
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://api.atris.site/parts
# health + JSON data + 405
```

## Pitfalls

- Docs saying Tilda → `:8080` are stale
- curl OK + browser CORS fail → origins list
- Empty public lists → no PUBLISHED entities
- Never commit real passwords; clear secrets from shell env after one-off imports
