import { onBeforeUnmount, onMounted, type Ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

export function useUnsavedGuard(dirty: Ref<boolean>, message = 'Есть несохранённые изменения. Уйти без сохранения?') {
  function beforeUnload(e: BeforeUnloadEvent) {
    if (!dirty.value) return
    e.preventDefault()
    e.returnValue = ''
  }

  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))

  onBeforeRouteLeave(() => {
    if (!dirty.value) return true
    return window.confirm(message)
  })

  watch(dirty, () => {
    /* reactive hook */
  })
}
