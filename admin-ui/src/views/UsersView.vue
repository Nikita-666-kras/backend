<script setup lang="ts">
import { ref } from 'vue'
import { createUser } from '@/api/posts'

const username = ref('')
const email = ref('')
const password = ref('')
const role = ref('EDITOR')
const message = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  loading.value = true
  message.value = ''
  error.value = ''
  try {
    await createUser({
      username: username.value,
      email: email.value,
      password: password.value,
      roles: [role.value]
    })
    message.value = `Пользователь ${username.value} создан`
    username.value = ''
    email.value = ''
    password.value = ''
    role.value = 'EDITOR'
  } catch (e: any) {
    error.value = e?.response?.data?.message || 'Не удалось создать пользователя'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Доступ</p>
        <h1>Пользователи</h1>
      </div>
    </header>

    <form class="card form" @submit.prevent="submit">
      <div class="field">
        <label>Логин</label>
        <input v-model="username" required />
      </div>
      <div class="field">
        <label>Email</label>
        <input v-model="email" type="email" required />
      </div>
      <div class="field">
        <label>Пароль</label>
        <input v-model="password" type="password" minlength="8" required />
      </div>
      <div class="field">
        <label>Роль</label>
        <select v-model="role">
          <option value="EDITOR">Редактор</option>
          <option value="ADMIN">Администратор</option>
        </select>
      </div>
      <button class="btn" :disabled="loading">{{ loading ? 'Создаём…' : 'Создать пользователя' }}</button>
      <p v-if="message" class="ok">{{ message }}</p>
      <p v-if="error" class="error">{{ error }}</p>
    </form>
  </section>
</template>

<style scoped>
.page-header {
  margin-bottom: 1rem;
}

.eyebrow {
  margin: 0;
  color: var(--accent);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-size: 0.75rem;
  font-weight: 600;
}

h1 {
  margin: 0.25rem 0 0;
  font-family: var(--font-serif);
  font-size: 2.4rem;
}

.form {
  max-width: 520px;
  padding: 1.25rem;
  display: grid;
  gap: 0.9rem;
}

.ok {
  color: var(--accent);
  margin: 0;
}

.error {
  color: var(--danger);
  margin: 0;
}
</style>
