/**
 * Import T50 kits from d:\Agent\agi-parts-images\T50 into blog-platform.
 *
 * Folder name = kit name (+ SKU in name). Cover photo = schema.jpg / schema.png.
 *
 * Usage:
 *   node scripts/import-t50-kits.mjs --base https://admin.atris.site --user admin --pass '...'
 *   node scripts/import-t50-kits.mjs --base http://localhost:8088 --user admin --pass 'Admin123!' --dry-run
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve('d:/Agent/agi-parts-images/T50')
const SKU_RE = /[A-Z]{2}\.[A-Z0-9][A-Z0-9.]*[A-Z0-9]/gi

function arg(flag, fallback = '') {
  const i = process.argv.indexOf(flag)
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback
}

const BASE = arg('--base', 'https://admin.atris.site').replace(/\/$/, '')
const USER = arg('--user', 'admin')
const PASS = arg('--pass', process.env.ADMIN_PASS || '')
const DRY = process.argv.includes('--dry-run')
const LIMIT = Number(arg('--limit', '0')) || 0
const PUBLISH = process.argv.includes('--publish')
const DRONE_NAME = arg('--drone', 'DJI Agras T50')

if (!PASS && !DRY) {
  console.error('Need --pass or ADMIN_PASS')
  process.exit(1)
}

function parseKits() {
  const kits = []
  for (const dir of fs.readdirSync(ROOT, { withFileTypes: true })) {
    if (!dir.isDirectory()) continue
    const full = path.join(ROOT, dir.name)
    const skus = dir.name.match(SKU_RE) || []
    const sku = skus.length ? skus[skus.length - 1] : null
    let schema = null
    for (const f of fs.readdirSync(full)) {
      const stem = path.parse(f).name.toLowerCase()
      const ext = path.extname(f).toLowerCase()
      if (['.jpg', '.jpeg', '.png', '.webp'].includes(ext) && ['schema', 'схема', 'shema'].includes(stem)) {
        schema = path.join(full, f)
        break
      }
    }
    let name = dir.name.replace(/,?\s*_?арт\.?_?.*$/i, '').replace(/_/g, ' ').trim().replace(/^[, ]+|[, ]+$/g, '')
    if (!name) name = dir.name
    kits.push({ dir: dir.name, name, sku: sku || `T50-${kits.length + 1}`, schema, partSku: sku })
  }
  return kits
}

async function login() {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASS })
  })
  if (!res.ok) throw new Error(`login ${res.status}: ${await res.text()}`)
  const json = await res.json()
  const data = json.data || json
  const token = data.accessToken || data.access_token || data.token
  if (!token) throw new Error('no accessToken in login response: ' + JSON.stringify(json).slice(0, 300))
  return token
}

async function api(token, method, urlPath, body, isForm = false) {
  const headers = { Authorization: `Bearer ${token}` }
  let payload
  if (isForm) {
    payload = body
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(`${BASE}${urlPath}`, { method, headers, body: payload })
  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = { raw: text }
  }
  if (!res.ok) {
    const msg = json?.message || json?.raw || text || res.statusText
    const err = new Error(`${method} ${urlPath} → ${res.status}: ${msg}`)
    err.status = res.status
    err.json = json
    throw err
  }
  return json?.data ?? json
}

async function ensureDrone(token) {
  const page = await api(token, 'GET', `/admin/drones?size=100`)
  const found = (page.content || []).find(
    (d) => d.name.toLowerCase() === DRONE_NAME.toLowerCase() || d.name.toLowerCase().includes('t50')
  )
  if (found) return found
  return api(token, 'POST', '/admin/drones', {
    name: DRONE_NAME,
    description: 'Agras T50',
    status: 'PUBLISHED'
  })
}

async function findPartBySku(token, sku) {
  if (!sku) return null
  const page = await api(token, 'GET', `/admin/parts?q=${encodeURIComponent(sku)}&size=20`)
  return (page.content || []).find((p) => String(p.sku).toUpperCase() === String(sku).toUpperCase()) || null
}

async function findKitBySku(token, sku) {
  const page = await api(token, 'GET', `/admin/kits?q=${encodeURIComponent(sku)}&size=20`)
  return (page.content || []).find((k) => String(k.sku).toUpperCase() === String(sku).toUpperCase()) || null
}

async function uploadSchema(token, filePath) {
  const buf = fs.readFileSync(filePath)
  const blob = new Blob([buf], { type: mime(filePath) })
  const form = new FormData()
  form.append('file', blob, path.basename(filePath))
  form.append('section', 'PARTS')
  return api(token, 'POST', '/admin/media', form, true)
}

function mime(filePath) {
  const ext = path.extname(filePath).toLowerCase()
  if (ext === '.png') return 'image/png'
  if (ext === '.webp') return 'image/webp'
  return 'image/jpeg'
}

async function upsertKit(token, kit, droneId, partId, coverMediaId) {
  const items = partId ? [{ partId, qty: 1 }] : []
  const payload = {
    name: kit.name.slice(0, 240),
    sku: kit.sku.slice(0, 120),
    description: `T50 · ${kit.dir}`,
    price: 0,
    currency: 'RUB',
    priceMode: 'MANUAL',
    droneId,
    coverMediaId: coverMediaId || null,
    mediaIds: coverMediaId ? [coverMediaId] : [],
    items,
    status: PUBLISH ? 'PUBLISHED' : 'DRAFT'
  }
  const existing = await findKitBySku(token, kit.sku)
  if (existing) {
    const updated = await api(token, 'PUT', `/admin/kits/${existing.id}`, payload)
    if (PUBLISH && updated.status !== 'PUBLISHED') {
      await api(token, 'POST', `/admin/kits/${existing.id}/publish`)
    }
    return { action: 'updated', id: existing.id }
  }
  const created = await api(token, 'POST', '/admin/kits', payload)
  if (PUBLISH && created.status !== 'PUBLISHED') {
    await api(token, 'POST', `/admin/kits/${created.id}/publish`)
  }
  return { action: 'created', id: created.id }
}

async function main() {
  let kits = parseKits()
  console.log(`Parsed ${kits.length} kits from ${ROOT}`)
  console.log(`sample: ${kits[0]?.name} | ${kits[0]?.sku} | ${kits[0]?.schema ? path.basename(kits[0].schema) : 'no schema'}`)
  fs.writeFileSync(path.join(__dirname, 't50-kits-manifest.json'), JSON.stringify(kits, null, 2), 'utf8')

  if (LIMIT > 0) kits = kits.slice(0, LIMIT)
  if (DRY) {
    console.log('Dry run — stop')
    return
  }

  const token = await login()
  console.log('Logged in')
  const drone = await ensureDrone(token)
  console.log(`Drone: ${drone.name} (${drone.id})`)

  let created = 0
  let updated = 0
  let failed = 0
  const errors = []

  for (let i = 0; i < kits.length; i++) {
    const kit = kits[i]
    process.stdout.write(`[${i + 1}/${kits.length}] ${kit.sku} … `)
    try {
      let coverId = null
      if (kit.schema && fs.existsSync(kit.schema)) {
        const media = await uploadSchema(token, kit.schema)
        coverId = media.id
      }
      const part = await findPartBySku(token, kit.partSku)
      const result = await upsertKit(token, kit, drone.id, part?.id || null, coverId)
      if (result.action === 'created') created++
      else updated++
      console.log(`${result.action}${part ? ' +part' : ' (no part)'}${coverId ? ' +photo' : ''}`)
    } catch (e) {
      failed++
      errors.push({ sku: kit.sku, message: e.message })
      console.log('FAIL ' + e.message)
    }
  }

  console.log(JSON.stringify({ created, updated, failed, errors: errors.slice(0, 20) }, null, 2))
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
