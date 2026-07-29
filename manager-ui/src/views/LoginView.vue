<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { redirectToAdminUi, isPurchaserOnly as userIsPurchaserOnly } from '@/utils/roles'

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

if (route.query.error === 'forbidden') {
  error.value = 'Доступ только для менеджеров и администраторов'
}

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    if (auth.isEditorOnly) {
      redirectToAdminUi('/posts')
      return
    }
    if (userIsPurchaserOnly(auth.user?.roles)) {
      redirectToAdminUi('/parts')
      return
    }
    if (!auth.canUseManagerUi) {
      await auth.logout()
      error.value = 'Недостаточно прав для входа в менеджерский хаб'
      return
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.push(redirect)
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    error.value = msg || 'Неверный логин или пароль'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="card login-card" @submit.prevent="submit">
      <p class="eyebrow">АТРИС</p>
      <h1>Менеджерский хаб</h1>
      <p class="muted">Калькулятор КП и рабочие инструменты</p>

      <div class="field">
        <label for="login-username">Логин</label>
        <input id="login-username" v-model="username" type="text" autocomplete="username" required />
      </div>

      <div class="field">
        <label for="login-password">Пароль</label>
        <input id="login-password" v-model="password" type="password" autocomplete="current-password" required />
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
}

.login-card h1 {
  margin: 0.2rem 0 0;
  font-size: 1.75rem;
  font-weight: 800;
}
</style>
