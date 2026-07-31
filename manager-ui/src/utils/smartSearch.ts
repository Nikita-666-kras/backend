import type { CatalogItem } from '@/types/kp'

const EN = "`qwertyuiop[]asdfghjkl;'zxcvbnm,./"
const RU = "ёйцукенгшщзхъфывапролджэячсмитьбю."

function swapLayoutChar(ch: string): string {
  const lower = ch.toLowerCase()
  const enIdx = EN.indexOf(lower)
  if (enIdx >= 0) {
    const mapped = RU[enIdx]
    return ch === lower ? mapped : mapped.toUpperCase()
  }
  const ruIdx = RU.indexOf(lower)
  if (ruIdx >= 0) {
    const mapped = EN[ruIdx]
    return ch === lower ? mapped : mapped.toUpperCase()
  }
  return ch
}

export function swapKeyboardLayout(text: string): string {
  return [...text].map(swapLayoutChar).join('')
}

export function normalizeSearch(text: string): string {
  return text
    .toLowerCase()
    .replace(/ё/g, 'е')
    .replace(/[^\p{L}\p{N}\s+-]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

export function searchTokens(query: string): string[] {
  return normalizeSearch(query).split(' ').filter(Boolean)
}

export function catalogSearchQueries(raw: string): string[] {
  const q = raw.trim()
  if (!q) return ['']
  const queries = [q]
  const swapped = swapKeyboardLayout(q)
  if (swapped !== q && /[а-яёa-z]/i.test(swapped)) {
    queries.push(swapped)
  }
  return queries
}

/** Чем выше — тем релевантнее. */
export function scoreCatalogItem(item: CatalogItem, tokens: string[]): number {
  if (!tokens.length) return 0
  const name = normalizeSearch(item.name || '')
  const sku = normalizeSearch(item.sku || '')
  const hay = `${sku} ${name}`

  if (!tokens.every((t) => hay.includes(t))) return -1

  let score = 0
  const joined = tokens.join(' ')
  if (sku === joined || sku === tokens[0]) score += 1000
  if (sku.startsWith(tokens[0])) score += 400
  if (sku.includes(tokens[0])) score += 200
  if (name.startsWith(joined)) score += 180
  if (name.startsWith(tokens[0])) score += 120
  for (const t of tokens) {
    if (sku.includes(t)) score += 60
    if (name.includes(t)) score += 40
  }
  score += Math.max(0, 40 - name.length / 4)
  return score
}

export function rankCatalogItems(items: CatalogItem[], query: string): CatalogItem[] {
  const tokenSets = catalogSearchQueries(query)
    .map(searchTokens)
    .filter((tokens) => tokens.length > 0)
  if (!tokenSets.length) return items

  return items
    .map((item) => ({
      item,
      score: Math.max(...tokenSets.map((tokens) => scoreCatalogItem(item, tokens)))
    }))
    .filter((x) => x.score >= 0)
    .sort((a, b) => b.score - a.score || a.item.name.localeCompare(b.item.name, 'ru'))
    .map((x) => x.item)
}

export function highlightMatch(text: string, query: string): Array<{ text: string; hit: boolean }> {
  const tokens = searchTokens(query).filter((t) => t.length >= 1)
  if (!text || !tokens.length) return [{ text, hit: false }]

  const source = text
  const lower = normalizeSearch(source)
  const ranges: Array<[number, number]> = []

  // map normalize positions roughly via scanning original with same rules is hard;
  // highlight case-insensitive substrings in original text
  for (const token of tokens) {
    const re = new RegExp(token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'ig')
    let m: RegExpExecArray | null
    while ((m = re.exec(source)) !== null) {
      ranges.push([m.index, m.index + m[0].length])
      if (m[0].length === 0) re.lastIndex++
    }
  }

  if (!ranges.length) {
    // fallback: try normalised match display without precise highlight
    if (tokens.every((t) => lower.includes(t))) return [{ text: source, hit: false }]
    return [{ text: source, hit: false }]
  }

  ranges.sort((a, b) => a[0] - b[0] || b[1] - a[1])
  const merged: Array<[number, number]> = []
  for (const r of ranges) {
    const last = merged[merged.length - 1]
    if (!last || r[0] > last[1]) merged.push([...r])
    else last[1] = Math.max(last[1], r[1])
  }

  const parts: Array<{ text: string; hit: boolean }> = []
  let cursor = 0
  for (const [start, end] of merged) {
    if (cursor < start) parts.push({ text: source.slice(cursor, start), hit: false })
    parts.push({ text: source.slice(start, end), hit: true })
    cursor = end
  }
  if (cursor < source.length) parts.push({ text: source.slice(cursor), hit: false })
  return parts
}
