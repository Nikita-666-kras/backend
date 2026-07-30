import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import api from '@/api/http'
import {
  canAccessCatalog as userCanAccessCatalog,
  canAccessEditorContent as userCanAccessEditorContent,
  canUseAdminUi as userCanUseAdminUi,
  hasRole,
  isEditorOnly as userIsEditorOnly,
  isManagerOnly as userIsManagerOnly,
  isPurchaserOnly as userIsPurchaserOnly,
  type Role
} from '@/utils/roles'
import {
  allowedMediaSectionOptions,
  canAccessMediaLibrary as userCanAccessMediaLibrary
} from '@/utils/mediaSections'

export type { Role }

export interface UserInfo {
  id: string
  username: string
  email: string
  roles: Role[]
  enabled: boolean
}

const ACCESS_KEY = 'accessToken'
const REFRESH_KEY = 'refreshToken'

function read(key: string) {
  return sessionStorage.getItem(key) || ''
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(read(ACCESS_KEY))
  const refreshToken = ref(read(REFRESH_KEY))
  const user = ref<UserInfo | null>(null)

  const isAuthenticated = computed(() => Boolean(accessToken.value))
  const isAdmin = computed(() => hasRole(user.value?.roles, 'ADMIN'))
  const isEditor = computed(() => hasRole(user.value?.roles, 'EDITOR'))
  const isManager = computed(() => hasRole(user.value?.roles, 'MANAGER'))
  const isPurchaser = computed(() => hasRole(user.value?.roles, 'PURCHASER'))
  const isEditorOnly = computed(() => userIsEditorOnly(user.value?.roles))
  const isPurchaserOnly = computed(() => userIsPurchaserOnly(user.value?.roles))
  const isManagerOnly = computed(() => userIsManagerOnly(user.value?.roles))
  const canUseAdminUi = computed(() => userCanUseAdminUi(user.value?.roles))
  const canAccessCatalog = computed(() => userCanAccessCatalog(user.value?.roles))
  const canAccessEditorContent = computed(() => userCanAccessEditorContent(user.value?.roles))
  const canAccessMediaLibrary = computed(() => userCanAccessMediaLibrary(user.value?.roles))
  const mediaSectionOptions = computed(() => allowedMediaSectionOptions(user.value?.roles))
  const canUseManagerUi = computed(() => hasRole(user.value?.roles, 'ADMIN') || hasRole(user.value?.roles, 'MANAGER'))

  function persist() {
    sessionStorage.setItem(ACCESS_KEY, accessToken.value)
    sessionStorage.setItem(REFRESH_KEY, refreshToken.value)
  }

  function clearSession() {
    accessToken.value = ''
    refreshToken.value = ''
    user.value = null
    sessionStorage.removeItem(ACCESS_KEY)
    sessionStorage.removeItem(REFRESH_KEY)
  }

  async function login(username: string, password: string) {
    const { data } = await api.post('/auth/login', { username, password })
    accessToken.value = data.data.accessToken
    refreshToken.value = data.data.refreshToken
    persist()
    await fetchMe()
  }

  async function refresh() {
    const { data } = await api.post('/auth/refresh', { refreshToken: refreshToken.value })
    accessToken.value = data.data.accessToken
    refreshToken.value = data.data.refreshToken
    persist()
  }

  async function fetchMe() {
    const { data } = await api.get('/auth/me')
    user.value = data.data
  }

  async function logout() {
    try {
      await api.post('/auth/logout', {
        refreshToken: refreshToken.value || undefined
      })
    } catch {
      // ignore logout errors
    }
    clearSession()
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    isAdmin,
    isEditor,
    isManager,
    isPurchaser,
    isEditorOnly,
    isPurchaserOnly,
    isManagerOnly,
    canUseAdminUi,
    canAccessCatalog,
    canAccessEditorContent,
    canAccessMediaLibrary,
    mediaSectionOptions,
    canUseManagerUi,
    login,
    refresh,
    fetchMe,
    clearSession,
    logout
  }
})
