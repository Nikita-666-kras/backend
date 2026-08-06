---
name: atris-tilda-blocks
description: >-
  Creates or updates ATRIS Tilda T123 HTML blocks (header, drones, blog, store,
  legal, calculators). Use when editing files under d:\Jobs\Atris, T123 widgets,
  or atris_*_tilda_guide.md.
---

# ATRIS Tilda blocks

## Rules

1. One self-contained T123 snippet (HTML+CSS+JS) unless the guide says otherwise.
2. Block top/bottom paddings in Tilda = **0**.
3. Prefer Manrope; dark pages often `#021228`.
4. Follow skill `atris-design-system` for look & copy.
5. Read the matching `*_tilda_guide.md` before inventing structure.

## Map

See `d:\Jobs\База знаний\knowledge\03-tilda-map.md`.

## Workflow

1. Find existing block/guide in `d:\Jobs\Atris\`
2. Match naming: `atris_<area>.html` or `atris_<area>_blocks/`
3. Keep one thought per section; real product imagery
4. Document publish steps in the guide if new page

## Do not

- Depend on external UI libs (except Google Fonts)
- Put secrets in client HTML
- Break site header/footer globals without checking `atris_header.html` / footer
