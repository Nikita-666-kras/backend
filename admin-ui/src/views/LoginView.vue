<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const username = ref('admin')
const password = ref('Admin123!')
const error = ref('')
const loading = ref(false)
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.push(redirect)
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Неверный логин или пароль'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="card login-card rise" @submit.prevent="submit">
      <p class="eyebrow">Редакция</p>
      <h1>Вход в админку</h1>
      <p class="lead muted">Посты, медиатека и публикация в одном месте.</p>

      <div class="field">
        <label>Логин</label>
        <input v-model="username" autocomplete="username" required />
      </div>
      <div class="field">
        <label>Пароль</label>
        <input v-model="password" type="password" autocomplete="current-password" required />
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <button class="btn" :disabled="loading">{{ loading ? 'Входим…' : 'Войти' }}</button>

      <div class="hint muted">
        <span>admin / Admin123!</span>
        <span>editor / Editor123!</span>
      </div>
    </form>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 1.5rem;
  background:
    radial-gradient(circle at 20% 20%, rgba(13, 107, 92, 0.16), transparent 40%),
    radial-gradient(circle at 80% 0%, rgba(18, 32, 28, 0.12), transparent 35%),
    linear-gradient(160deg, #eaf3f0, #f7faf8 55%);
}

.login-card {
  width: min(440px, 100%);
  padding: 2rem;
  display: grid;
  gap: 1rem;
}

.lead,
.hint {
  margin: 0;
}

.hint {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
}
</style>
