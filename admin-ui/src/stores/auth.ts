import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import api from '@/api/http'

export type Role = 'ADMIN' | 'EDITOR'

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
  const isAdmin = computed(() => user.value?.roles.includes('ADMIN') ?? false)

  function persist() {
    sessionStorage.setItem(ACCESS_KEY, accessToken.value)
    sessionStorage.setItem(REFRESH_KEY, refreshToken.value)
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
      if (refreshToken.value) {
        await api.post('/auth/logout', { refreshToken: refreshToken.value })
      }
    } catch {
      // ignore logout errors
    }
    accessToken.value = ''
    refreshToken.value = ''
    user.value = null
    sessionStorage.removeItem(ACCESS_KEY)
    sessionStorage.removeItem(REFRESH_KEY)
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    isAdmin,
    login,
    refresh,
    fetchMe,
    logout
  }
})
