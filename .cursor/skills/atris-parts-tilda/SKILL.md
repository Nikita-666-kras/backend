---
name: atris-parts-tilda
description: >-
  Edits the ATRIS parts page Tilda blocks (atris_parts_api_blocks): config,
  scheme with kits from API, catalog grid, categories tree. Use when working on
  /parts, zapchasti, kits, hotspots, or ATRIS_PARTS.
---

# ATRIS parts (Tilda)

## Path

`d:\Jobs\Atris\atris_parts_api_blocks\`

## Block order

1. `00_config.html`
2. `00_styles.html`
3. `01_hero.html`
4. `02_scheme.html`
5. `02_catalog.html`
6. `03_cta.html`

Do **not** require `01_t50_kits.html` (kits come from `GET /kits`).

## Behavior

- Models from `/drones` (value = drone `id`, URL `?model=slug`)
- Kits from `/kits?droneId=`
- Kit detail: `coverUrl` + `items[]` (`partSku`, `partName`, `qty`, `partPrice`)
- Categories: tree by `parentId`; sync catalog via `ATRIS_PARTS_ON_CATEGORY`
- Optional hotspot overlay: `ATRIS_PARTS.kitHotspots[sku]`

## When changing API usage

Follow skill `atris-public-api`. Keep `mediaUrl` / null price handling.

## Guide

`d:\Jobs\Atris\atris_parts_api_tilda_guide.md`  
KB: `d:\Jobs\База знаний\knowledge\05-parts-page.md`
