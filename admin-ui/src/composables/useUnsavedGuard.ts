import { onBeforeUnmount, onMounted, type Ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

/** Warn on browser leave / in-app navigation when dirty. */
export function useUnsavedGuard(dirty: Ref<boolean>, message = 'Есть несохранённые изменения. Уйти без сохранения?') {
  // Some navigation flows can trigger leave checks more than once.
  // After explicit user confirmation, allow this navigation pass-through.
  let allowNextLeave = false

  function beforeUnload(e: BeforeUnloadEvent) {
    if (!dirty.value || allowNextLeave) return
    e.preventDefault()
    e.returnValue = ''
  }

  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))

  onBeforeRouteLeave(() => {
    if (allowNextLeave) {
      allowNextLeave = false
      return true
    }
    if (!dirty.value) return true
    const accepted = window.confirm(message)
    if (accepted) allowNextLeave = true
    return accepted
  })

  watch(dirty, () => {
    /* keep reactive for leave hooks */
  })
}
