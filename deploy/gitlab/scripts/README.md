# Скрипты настройки прав GitLab (strict / free проекты)

На self-hosted **GitLab CE (Free)** обязательные «Approvals required» через API — это фича **Premium**.  
На CE надёжно автоматизируется через **Protected branches**:

| mode | Push в `main` | Merge в `main` | Смысл |
|------|---------------|----------------|--------|
| `strict` | никто | только **Maintainers** | изменения в main только через вас (или других Maintainer) |
| `free` | никто | **Developers** | MR есть, но ваше одобрение не нужно |
| `open` | Developers | Developers | песочница, можно пушить в main |

## 1. Токен

В GitLab: **Preferences → Access Tokens**

- Scopes: `api`
- Скопируйте токен

Узнайте свой user id (для Premium-approvals, опционально):

```bash
export GITLAB_URL=https://git.atris.site
export GITLAB_TOKEN=glpat-xxxx

curl -s -H "PRIVATE-TOKEN: $GITLAB_TOKEN" \
  "$GITLAB_URL/api/v4/user" | jq '{id, username}'
```

## 2. Один проект

На машине с `bash`, `curl`, `jq` (Linux / WSL / сервер):

```bash
cd deploy/gitlab/scripts
chmod +x protect-project.sh

export GITLAB_URL=https://git.atris.site
export GITLAB_TOKEN=glpat-xxxx

./protect-project.sh atris/blog-platform strict
./protect-project.sh atris/sandbox-demo free
./protect-project.sh atris/playground open
```

## 3. Массово из списка

Отредактируйте `projects.tsv`, затем:

```bash
./protect-project.sh --from-list projects.tsv
```

## 4. Premium: настоящие required approvals

Если когда-нибудь будет Premium/Ultimate:

```bash
export ENABLE_PREMIUM_APPROVALS=1
export APPROVER_USER_ID=2   # ваш id из /api/v4/user
./protect-project.sh atris/blog-platform strict
```

Скрипт создаст approval rule «Admin approve» с `approvals_required=1`.  
На CE это, скорее всего, вернёт ошибку — тогда остаются только protected branches (этого достаточно для «мержит только Maintainer»).

## 5. Роли в проекте

Скрипт **не** назначает роли. Нужно вручную (или отдельным API):

- вы — **Maintainer** или **Owner**
- обычные разработчики — **Developer**

Иначе `strict` не сработает как «только я».

Добавить участника через API:

```bash
# role: 30=Developer, 40=Maintainer
PROJECT=atris%2Fblog-platform
USER_ID=5

curl -s -X POST -H "PRIVATE-TOKEN: $GITLAB_TOKEN" \
  "$GITLAB_URL/api/v4/projects/$PROJECT/members" \
  --data "user_id=$USER_ID&access_level=30" | jq .
```

## 6. Что проверить после скрипта

1. Проект → **Settings → Repository → Protected branches** — `main` как ожидали  
2. Под Developer: `git push origin main` → отказ  
3. `strict`: Merge в UI доступен только Maintainer  
4. `free`: Developer может Merge своего MR без вас  

## Файлы

| Файл | Назначение |
|------|------------|
| `protect-project.sh` | API-скрипт |
| `projects.tsv` | список проектов и режимов |
| этот README | инструкция |
