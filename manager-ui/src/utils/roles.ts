export type Role = 'ADMIN' | 'EDITOR' | 'MANAGER' | 'PURCHASER'

export function hasRole(roles: Role[] | undefined, role: Role): boolean {
  return roles?.includes(role) ?? false
}

export function canUseAdminUi(roles: Role[] | undefined): boolean {
  return hasRole(roles, 'ADMIN') || hasRole(roles, 'EDITOR') || hasRole(roles, 'PURCHASER') || hasRole(roles, 'MANAGER')
}

export function canUseManagerUi(roles: Role[] | undefined): boolean {
  return hasRole(roles, 'ADMIN') || hasRole(roles, 'MANAGER')
}

export function isEditorOnly(roles: Role[] | undefined): boolean {
  return hasRole(roles, 'EDITOR') && !hasRole(roles, 'ADMIN') && !hasRole(roles, 'MANAGER') && !hasRole(roles, 'PURCHASER')
}

export function isPurchaserOnly(roles: Role[] | undefined): boolean {
  return hasRole(roles, 'PURCHASER') && !hasRole(roles, 'ADMIN') && !hasRole(roles, 'MANAGER') && !hasRole(roles, 'EDITOR')
}

export function adminUiUrl(): string {
  return import.meta.env.VITE_ADMIN_UI_URL || 'http://localhost:8088'
}

export function redirectToAdminUi(path = '/posts'): void {
  window.location.replace(`${adminUiUrl()}${path}`)
}
