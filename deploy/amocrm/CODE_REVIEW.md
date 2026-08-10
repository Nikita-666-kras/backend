# Code review: AutoAR (updated 2026-08-10)

**Skill:** `amocrm-integrations`  
**Docs:** [Salesbot](https://www.amocrm.ru/developers/content/digital_pipeline/salesbot) · [continue](https://www.amocrm.ru/developers/content/crm_platform/widgets-api) · [BEST_PRACTICES.md](BEST_PRACTICES.md)

## Mechanism

Digital Pipeline → Salesbot `widget_request` → external continue → bot `set_custom_fields`.  
Not a private JS widget package.

## Alignments with official docs (after correction)

| Rule | Implementation |
|------|----------------|
| 200 within ~2s | Fast extract + RestClient timeouts 0.8s/1.5s |
| `return_url` at root | `@JsonProperty("return_url")` |
| Continue `{ "data": { keys } }` → `{{json.key}}` | `ar` + `status` |
| `execute_handlers` only show/goto | **Not** used for field write |
| Field write in bot | `set_custom_fields` after condition on `{{json.status}}` |
| Official bot pattern | `widget_request` + `goto` then conditions (see `autoar.json`) |

## Remaining optional debt

- Validate amo JWT with integration secret
- `AMOCRM_WEBHOOK_SECRET` on public URL
- Structured request-id logging

## Launch blocker (ops)

Stage trigger must be **after transition or creation**. Otherwise nginx never sees amo POST.
