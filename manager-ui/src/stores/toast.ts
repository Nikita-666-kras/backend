import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastKind = 'ok' | 'error' | 'info'

export interface ToastItem {
  id: number
  kind: ToastKind
  message: string
}

let seq = 1

export const useToastStore = defineStore('toast', () => {
  const items = ref<ToastItem[]>([])

  function push(kind: ToastKind, message: string, ttlMs = 4200) {
    const id = seq++
    items.value = [...items.value, { id, kind, message }]
    window.setTimeout(() => dismiss(id), ttlMs)
  }

  function ok(message: string) {
    push('ok', message)
  }

  function error(message: string) {
    push('error', message, 6500)
  }

  function info(message: string) {
    push('info', message)
  }

  function dismiss(id: number) {
    items.value = items.value.filter((t) => t.id !== id)
  }

  return { items, push, ok, error, info, dismiss }
})
