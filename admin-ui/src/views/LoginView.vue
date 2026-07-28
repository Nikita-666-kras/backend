<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const username = ref('')
const password = ref('')
const showPassword = ref(false)
const error = ref('')
const loading = ref(false)
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

/** Пробелы показываем как ·, чтобы их было видно. */
function withVisibleSpaces(value: string) {
  return value.replaceAll(' ', '·')
}

const usernameMirror = computed(() => withVisibleSpaces(username.value))

const passwordMirror = computed(() => {
  if (showPassword.value) return withVisibleSpaces(password.value)
  return [...password.value].map((ch) => (ch === ' ' ? '·' : '•')).join('')
})

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
      <p class="eyebrow">АТРИС</p>
      <h1>Вход в админку</h1>

      <div class="field">
        <label for="login-username">Логин</label>
        <div class="reveal-field">
          <div class="mirror" aria-hidden="true">{{ usernameMirror }}<span class="caret-space">&nbsp;</span></div>
          <input
            id="login-username"
            v-model="username"
            class="reveal-input"
            type="text"
            autocomplete="username"
            required
            spellcheck="false"
          />
        </div>
      </div>

      <div class="field">
        <label for="login-password">Пароль</label>
        <div class="reveal-field has-toggle">
          <div class="mirror" aria-hidden="true">{{ passwordMirror }}<span class="caret-space">&nbsp;</span></div>
          <input
            id="login-password"
            v-model="password"
            class="reveal-input"
            type="text"
            autocomplete="current-password"
            required
            spellcheck="false"
            :aria-label="showPassword ? 'Пароль (видимый)' : 'Пароль (скрытый)'"
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

.reveal-field {
  position: relative;
  display: grid;
}

.reveal-field.has-toggle .reveal-input,
.reveal-field.has-toggle .mirror {
  padding-right: 5.6rem;
}

.mirror {
  grid-area: 1 / 1;
  border: 1px solid transparent;
  border-radius: 12px;
  padding: 0.75rem 0.9rem;
  white-space: pre;
  overflow: hidden;
  color: var(--ink);
  pointer-events: none;
  font: inherit;
  line-height: normal;
  letter-spacing: 0.02em;
}

.mirror .caret-space {
  visibility: hidden;
}

.reveal-input {
  grid-area: 1 / 1;
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 0.75rem 0.9rem;
  background: var(--input-bg);
  color: transparent;
  caret-color: var(--accent);
  font: inherit;
  line-height: normal;
  letter-spacing: 0.02em;
}

.reveal-input:focus {
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
