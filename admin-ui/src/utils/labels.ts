const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Черновик',
  PUBLISHED: 'Опубликован',
  ARCHIVED: 'В архиве'
}

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Администратор',
  EDITOR: 'Редактор'
}

export function statusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status
}

export function roleLabel(role: string): string {
  return ROLE_LABELS[role] ?? role
}

export function rolesLabel(roles: string[] | undefined): string {
  if (!roles?.length) return ''
  return roles.map(roleLabel).join(', ')
}
