<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { redirectToManagerHub, defaultAdminRoute } from '@/utils/roles'

function resolveRedirect(target: string) {
  if (!target.includes('?')) return target
  const [path, query] = target.split('?')
  const params = Object.fromEntries(new URLSearchParams(query))
  return { path, query: params }
}

const username = ref('')
const password = ref('')
const showPassword = ref(false)
const error = ref('')
const loading = ref(false)
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

if (route.query.error === 'forbidden') {
  error.value = 'Недостаточно прав для входа в админку'
}

async function submit() {
  if (loading.value) return
  error.value = ''
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    if (auth.isManagerOnly && auth.canUseManagerUi && !route.query.redirect) {
      redirectToManagerHub()
      return
    }
    if (!auth.canUseAdminUi) {
      await auth.logout()
      error.value = 'Недостаточно прав для входа в админку'
      return
    }
    const redirect = typeof route.query.redirect === 'string'
      ? route.query.redirect
      : defaultAdminRoute(auth.user?.roles)
    await router.push(resolveRedirect(redirect))
  } catch (e: any) {
    if (e?.response?.status === 429) {
      error.value = 'Слишком много попыток входа. Подождите немного и попробуйте снова.'
    } else {
      error.value = e?.response?.data?.message || 'Неверный логин или пароль'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="card login-card rise" @submit.prevent="submit">
      <p class="eyebrow">АТРИС</p>
      <h1>Вход в админку</h1>

      <div class="field">
        <label for="login-username">Логин</label>
        <input id="login-username" v-model="username" type="text" autocomplete="username" required spellcheck="false" />
      </div>

      <div class="field">
        <label for="login-password">Пароль</label>
        <div class="password-field">
          <input
            id="login-password"
            v-model="password"
            class="password-input"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            required
            spellcheck="false"
          />
          <button
            class="toggle"
            type="button"
            :aria-pressed="showPassword"
            :aria-label="showPassword ? 'Скрыть пароль' : 'Показать пароль'"
            @click="showPassword = !showPassword"
          >
            {{ showPassword ? 'Скрыть' : 'Показать' }}
          </button>
        </div>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <button class="btn" :disabled="loading">{{ loading ? 'Входим…' : 'Войти' }}</button>
    </form>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 1.5rem;
}

.login-card {
  width: min(440px, 100%);
  padding: 2rem;
  display: grid;
  gap: 1rem;
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.password-field {
  position: relative;
}

.password-input {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 0.75rem 5.8rem 0.75rem 0.9rem;
  background: var(--input-bg);
  color: var(--ink);
  caret-color: var(--accent);
}

.password-input:focus {
  outline: 2px solid rgba(141, 198, 63, 0.28);
  border-color: rgba(141, 198, 63, 0.55);
}

.toggle {
  position: absolute;
  right: 0.45rem;
  top: 50%;
  transform: translateY(-50%);
  border: 1px solid rgba(141, 198, 63, 0.35);
  background: rgba(141, 198, 63, 0.08);
  color: var(--ink);
  border-radius: 8px;
  padding: 0.35rem 0.55rem;
  font-size: 0.78rem;
  font-weight: 700;
}

.toggle:hover {
  background: rgba(141, 198, 63, 0.16);
}
</style>
