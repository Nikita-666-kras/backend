import type { MediaSection } from '@/api/media'
import type { Role } from '@/utils/roles'
import { hasRole } from '@/utils/roles'

export interface MediaSectionConfig {
  value: MediaSection
  label: string
  icon: string
}

export const ALL_MEDIA_SECTIONS: MediaSectionConfig[] = [
  { value: 'PARTS', label: 'Запчасти', icon: 'package' },
  { value: 'DRONES', label: 'Дроны', icon: 'drone' },
  { value: 'ARTICLES', label: 'Статьи', icon: 'file-text' },
  { value: 'SERVICE', label: 'Сервис', icon: 'wrench' },
  { value: 'TRAILERS', label: 'Прицепы', icon: 'truck' },
  { value: 'EDUCATION', label: 'Обучение', icon: 'book-open' },
  { value: 'OTHER', label: 'Другое', icon: 'folder' }
]

const sectionByValue = Object.fromEntries(ALL_MEDIA_SECTIONS.map((s) => [s.value, s])) as Record<
  MediaSection,
  MediaSectionConfig
>

export function mediaSectionLabel(value: MediaSection): string {
  return sectionByValue[value]?.label ?? value
}

export function canAccessMediaLibrary(roles: Role[] | undefined): boolean {
  return (
    hasRole(roles, 'ADMIN') ||
    hasRole(roles, 'EDITOR') ||
    hasRole(roles, 'MANAGER') ||
    hasRole(roles, 'PURCHASER')
  )
}

export function allowedMediaSections(roles: Role[] | undefined): MediaSection[] {
  if (hasRole(roles, 'ADMIN')) {
    return ALL_MEDIA_SECTIONS.map((s) => s.value)
  }

  if (
    hasRole(roles, 'MANAGER') &&
    !hasRole(roles, 'EDITOR') &&
    !hasRole(roles, 'PURCHASER')
  ) {
    return ['OTHER']
  }

  const allowed = new Set<MediaSection>()

  if (hasRole(roles, 'EDITOR')) {
    allowed.add('ARTICLES')
    allowed.add('EDUCATION')
  }
  if (hasRole(roles, 'PURCHASER')) {
    allowed.add('PARTS')
    allowed.add('DRONES')
    allowed.add('SERVICE')
  }
  if (hasRole(roles, 'MANAGER')) {
    allowed.add('OTHER')
  }

  allowed.add('OTHER')
  return ALL_MEDIA_SECTIONS.filter((s) => allowed.has(s.value)).map((s) => s.value)
}

export function allowedMediaSectionOptions(roles: Role[] | undefined): MediaSectionConfig[] {
  const allowed = new Set(allowedMediaSections(roles))
  return ALL_MEDIA_SECTIONS.filter((s) => allowed.has(s.value))
}

export function canAccessMediaSection(roles: Role[] | undefined, section: MediaSection | ''): boolean {
  if (!section) return hasRole(roles, 'ADMIN')
  return allowedMediaSections(roles).includes(section as MediaSection)
}

export function defaultMediaSection(roles: Role[] | undefined): MediaSection {
  const allowed = allowedMediaSections(roles)
  if (hasRole(roles, 'MANAGER') && !hasRole(roles, 'ADMIN')) return 'OTHER'
  if (hasRole(roles, 'PURCHASER') && !hasRole(roles, 'ADMIN')) return 'PARTS'
  if (hasRole(roles, 'EDITOR') && !hasRole(roles, 'ADMIN')) return 'ARTICLES'
  return allowed[0] ?? 'OTHER'
}

export function normalizeMediaSectionQuery(
  roles: Role[] | undefined,
  raw: string | undefined | null
): MediaSection {
  const allowed = allowedMediaSections(roles)
  if (raw) {
    const upper = raw.toUpperCase() as MediaSection
    if (allowed.includes(upper)) return upper
  }
  return defaultMediaSection(roles)
}
