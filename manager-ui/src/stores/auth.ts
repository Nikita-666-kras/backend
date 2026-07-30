import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import api from '@/api/http'
import {
  canUseManagerUi as userCanUseManagerUi,
  hasRole,
  isEditorOnly as userIsEditorOnly,
  type Role
} from '@/utils/roles'

export type { Role }

export interface UserInfo {
  id: string
  username: string
  email: string
  roles: Role[]
  enabled: boolean
}

const ACCESS_KEY = 'manager_access_token'
const REFRESH_KEY = 'manager_refresh_token'

function read(key: string) {
  return sessionStorage.getItem(key) || ''
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(read(ACCESS_KEY))
  const refreshToken = ref(read(REFRESH_KEY))
  const user = ref<UserInfo | null>(null)

  const isAuthenticated = computed(() => Boolean(accessToken.value))
  const isAdmin = computed(() => hasRole(user.value?.roles, 'ADMIN'))
  const isManager = computed(() => hasRole(user.value?.roles, 'MANAGER'))
  const isEditor = computed(() => hasRole(user.value?.roles, 'EDITOR'))
  const isEditorOnly = computed(() => userIsEditorOnly(user.value?.roles))
  const canUseManagerUi = computed(() => userCanUseManagerUi(user.value?.roles))
  const canUseAdminUi = computed(() => hasRole(user.value?.roles, 'ADMIN') || hasRole(user.value?.roles, 'EDITOR'))

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
      // ignore
    }
    clearSession()
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    isAdmin,
    isManager,
    isEditor,
    isEditorOnly,
    canUseManagerUi,
    canUseAdminUi,
    login,
    refresh,
    fetchMe,
    clearSession,
    logout
  }
})
