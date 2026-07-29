export type Role = 'ADMIN' | 'EDITOR' | 'MANAGER' | 'PURCHASER'

export function hasRole(roles: Role[] | undefined, role: Role): boolean {
  return roles?.includes(role) ?? false
}

export function canUseAdminUi(roles: Role[] | undefined): boolean {
  return (
    hasRole(roles, 'ADMIN') ||
    hasRole(roles, 'EDITOR') ||
    hasRole(roles, 'PURCHASER') ||
    hasRole(roles, 'MANAGER')
  )
}

export function canAccessCatalog(roles: Role[] | undefined): boolean {
  return hasRole(roles, 'ADMIN') || hasRole(roles, 'PURCHASER')
}

export function canAccessEditorContent(roles: Role[] | undefined): boolean {
  return hasRole(roles, 'ADMIN') || hasRole(roles, 'EDITOR')
}

export function canUseManagerUi(roles: Role[] | undefined): boolean {
  return hasRole(roles, 'ADMIN') || hasRole(roles, 'MANAGER')
}

export function isManagerOnly(roles: Role[] | undefined): boolean {
  return hasRole(roles, 'MANAGER') && !hasRole(roles, 'ADMIN') && !hasRole(roles, 'EDITOR') && !hasRole(roles, 'PURCHASER')
}

export function isEditorOnly(roles: Role[] | undefined): boolean {
  return hasRole(roles, 'EDITOR') && !hasRole(roles, 'ADMIN')
}

export function isPurchaserOnly(roles: Role[] | undefined): boolean {
  return hasRole(roles, 'PURCHASER') && !hasRole(roles, 'ADMIN')
}

export function defaultAdminRoute(roles: Role[] | undefined): string {
  if (isManagerOnly(roles)) return '/media?section=OTHER'
  if (isPurchaserOnly(roles)) return '/parts'
  if (isEditorOnly(roles)) return '/posts'
  return '/'
}

export function managerUiUrl(): string {
  return import.meta.env.VITE_MANAGER_UI_URL || 'http://localhost:8090'
}

export function redirectToManagerHub(): void {
  window.location.replace(managerUiUrl())
}
