/** Public blog base URL (not admin, not gateway API). */
export function publicSiteOrigin(): string {
  const fromEnv = import.meta.env.VITE_PUBLIC_SITE_URL?.trim()
  if (fromEnv) return fromEnv.replace(/\/$/, '')
  const origin = window.location.origin
  if (origin.includes(':8088')) return origin.replace(':8088', ':8089')
  if (origin.includes(':5173')) return origin.replace(':5173', ':8089')
  return origin
}

export function publicPostUrl(slug: string): string {
  return `${publicSiteOrigin()}/posts/${slug}`
}
