#!/usr/bin/env bash
# Настройка protected branch main для проекта GitLab.
#
# Режимы (работают на GitLab CE / Free):
#   strict  — в main пушить нельзя; merge только Maintainers (фактически «через вас»)
#   free    — в main пушить нельзя; merge могут Developers + Maintainers (без вашего ОК)
#   open    — Developers могут и push, и merge в main (песочница)
#
# Требуется: curl, jq
# Токен: Preferences → Access Tokens → scopes: api
#
# Примеры:
#   export GITLAB_URL=https://git.atris.site
#   export GITLAB_TOKEN=glpat-xxxx
#   export APPROVER_USER_ID=2          # ваш user id (для Premium approvals)
#
#   ./protect-project.sh atris/blog-platform strict
#   ./protect-project.sh atris/sandbox-app free
#   ./protect-project.sh atris/playground open
#
# Массово из списка:
#   ./protect-project.sh --from-list projects.tsv

set -euo pipefail

GITLAB_URL="${GITLAB_URL:-https://git.atris.site}"
GITLAB_TOKEN="${GITLAB_TOKEN:?Set GITLAB_TOKEN (Personal Access Token with api scope)}"
# Опционально: принудительные approvals (нужен GitLab Premium/Ultimate)
ENABLE_PREMIUM_APPROVALS="${ENABLE_PREMIUM_APPROVALS:-0}"
APPROVER_USER_ID="${APPROVER_USER_ID:-}"

api() {
  local method=$1 path=$2
  shift 2
  curl -sS -X "$method" \
    -H "PRIVATE-TOKEN: ${GITLAB_TOKEN}" \
    -H "Content-Type: application/json" \
    "${GITLAB_URL}/api/v4${path}" \
    "$@"
}

urlencode() {
  # jq @uri
  printf '%s' "$1" | jq -sRr @uri
}

project_id() {
  local path=$1
  local enc
  enc=$(urlencode "$path")
  api GET "/projects/${enc}" | jq -r '.id'
}

unprotect_main() {
  local pid=$1
  # игнорируем 404, если ветка ещё не protected
  curl -sS -o /dev/null -w "%{http_code}" -X DELETE \
    -H "PRIVATE-TOKEN: ${GITLAB_TOKEN}" \
    "${GITLAB_URL}/api/v4/projects/${pid}/protected_branches/main" \
    | grep -Eq '^(204|404)$' || true
}

protect_strict() {
  local pid=$1
  unprotect_main "$pid"
  api POST "/projects/${pid}/protected_branches" \
    -d '{
      "name": "main",
      "allow_force_push": false,
      "allowed_to_push": [{"access_level": 0}],
      "allowed_to_merge": [{"access_level": 40}]
    }' | jq '{name, merge_access_levels, push_access_levels}'
  # access_level: 0=No one, 30=Developer, 40=Maintainer
}

protect_free() {
  local pid=$1
  unprotect_main "$pid"
  api POST "/projects/${pid}/protected_branches" \
    -d '{
      "name": "main",
      "allow_force_push": false,
      "allowed_to_push": [{"access_level": 0}],
      "allowed_to_merge": [{"access_level": 30}]
    }' | jq '{name, merge_access_levels, push_access_levels}'
}

protect_open() {
  local pid=$1
  unprotect_main "$pid"
  api POST "/projects/${pid}/protected_branches" \
    -d '{
      "name": "main",
      "allow_force_push": false,
      "allowed_to_push": [{"access_level": 30}],
      "allowed_to_merge": [{"access_level": 30}]
    }' | jq '{name, merge_access_levels, push_access_levels}'
}

# Premium only: обязательный Approve от конкретного пользователя
set_premium_approval() {
  local pid=$1 mode=$2
  if [[ "$ENABLE_PREMIUM_APPROVALS" != "1" ]]; then
    return 0
  fi
  if [[ -z "$APPROVER_USER_ID" ]]; then
    echo "WARN: ENABLE_PREMIUM_APPROVALS=1 but APPROVER_USER_ID empty — skip" >&2
    return 0
  fi

  # удалить существующие regular rules (best-effort)
  local rules
  rules=$(api GET "/projects/${pid}/approval_rules" || echo '[]')
  if echo "$rules" | jq -e 'type=="array"' >/dev/null 2>&1; then
    echo "$rules" | jq -r '.[].id // empty' | while read -r rid; do
      [[ -n "$rid" ]] || continue
      api DELETE "/projects/${pid}/approval_rules/${rid}" >/dev/null || true
    done
  else
    echo "NOTE: approval_rules API недоступен (нужен Premium) — используем только protected branches" >&2
    return 0
  fi

  if [[ "$mode" == "strict" ]]; then
    api POST "/projects/${pid}/approval_rules" \
      -d "$(jq -n \
        --arg name "Admin approve" \
        --argjson req 1 \
        --argjson uid "$APPROVER_USER_ID" \
        '{name:$name, approvals_required:$req, user_ids:[$uid], applies_to_all_protected_branches:true}')" \
      | jq '{id, name, approvals_required, eligible_approvers: [.eligible_approvers[]?.username]}' \
      || echo "NOTE: не удалось создать approval rule (Premium?)" >&2
  fi
}

apply_mode() {
  local project_path=$1 mode=$2
  local pid
  echo "==> ${project_path} → ${mode}"
  pid=$(project_id "$project_path")
  if [[ -z "$pid" || "$pid" == "null" ]]; then
    echo "ERROR: project not found: ${project_path}" >&2
    return 1
  fi
  echo "    project id: ${pid}"

  case "$mode" in
    strict) protect_strict "$pid" ;;
    free)   protect_free "$pid" ;;
    open)   protect_open "$pid" ;;
    *)
      echo "ERROR: unknown mode '${mode}' (strict|free|open)" >&2
      return 1
      ;;
  esac

  set_premium_approval "$pid" "$mode"
  echo "    OK"
}

usage() {
  cat <<'EOF'
Usage:
  protect-project.sh <group/project> <strict|free|open>
  protect-project.sh --from-list projects.tsv

projects.tsv format (TAB or space):
  atris/blog-platform   strict
  atris/sandbox-app     free
  atris/playground      open

Env:
  GITLAB_URL                 default https://git.atris.site
  GITLAB_TOKEN               required (api scope)
  ENABLE_PREMIUM_APPROVALS   0|1 — создать обязательные approval rules (Premium)
  APPROVER_USER_ID           ваш numeric user id
EOF
}

main() {
  command -v jq >/dev/null || { echo "Install jq"; exit 1; }
  command -v curl >/dev/null || { echo "Install curl"; exit 1; }

  if [[ "${1:-}" == "-h" || "${1:-}" == "--help" || $# -eq 0 ]]; then
    usage
    exit 0
  fi

  if [[ "$1" == "--from-list" ]]; then
    local file=${2:?Need path to list file}
    while read -r project mode _; do
      [[ -z "${project:-}" || "$project" =~ ^# ]] && continue
      apply_mode "$project" "$mode"
    done < "$file"
    exit 0
  fi

  if [[ $# -lt 2 ]]; then
    usage
    exit 1
  fi
  apply_mode "$1" "$2"
}

main "$@"
