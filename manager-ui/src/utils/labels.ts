const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Администратор',
  EDITOR: 'Редактор',
  MANAGER: 'Менеджер',
  PURCHASER: 'Закупщик'
}

export function roleLabel(role: string): string {
  return ROLE_LABELS[role] ?? role
}

export function rolesLabel(roles: string[] | undefined): string {
  if (!roles?.length) return ''
  return roles.map(roleLabel).join(', ')
}
