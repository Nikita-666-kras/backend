<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  createUser,
  deleteUser,
  fetchUsers,
  updateUserEnabled,
  updateUserRoles,
  type AdminUser
} from '@/api/posts'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { rolesLabel } from '@/utils/labels'

const auth = useAuthStore()
const toast = useToastStore()
const users = ref<AdminUser[]>([])
const q = ref('')
const username = ref('')
const email = ref('')
const password = ref('')
const showPassword = ref(false)
const role = ref('EDITOR')
const loading = ref(false)
const saving = ref(false)
const formError = ref('')

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{10,120}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** Prevent password-manager autofill from locking/styling create-user fields. */
function unlockAutofill(event: Event) {
  const el = event.target as HTMLInputElement
  el.removeAttribute('readonly')
}

function mapCreateError(e: any): string {
  const data = e?.response?.data
  const details: string[] = Array.isArray(data?.details) ? data.details : []
  const mapped = details.map((d) => {
    const text = String(d)
    if (text.includes('email')) return 'Укажите корректный email, например user@company.com'
    if (text.includes('Password must contain') || text.includes('password:')) {
      return 'Пароль: минимум 10 символов, заглавная, строчная буква и цифра'
    }
    if (text.includes('username')) return 'Логин: от 3 до 50 символов'
    return text
  })
  if (mapped.length) return [...new Set(mapped)].join('. ')
  const message = String(data?.message || '')
  if (message === 'Username already exists') return 'Такой логин уже занят'
  if (message === 'Email already exists') return 'Такой email уже занят'
  return message || 'Не удалось создать пользователя'
}

function validateForm(): string | null {
  const name = username.value.trim()
  const mail = email.value.trim()
  const pass = password.value
  if (name.length < 3) return 'Логин: минимум 3 символа'
  if (!EMAIL_PATTERN.test(mail)) return 'Укажите корректный email, например user@company.com'
  if (!PASSWORD_PATTERN.test(pass)) {
    return 'Пароль: минимум 10 символов, заглавная, строчная буква и цифра'
  }
  return null
}

async function load() {
  loading.value = true
  try {
    users.value = await fetchUsers(q.value || undefined)
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось загрузить пользователей')
  } finally {
    loading.value = false
  }
}

async function submit() {
  formError.value = ''
  const localError = validateForm()
  if (localError) {
    formError.value = localError
    return
  }
  if (saving.value) return
  saving.value = true
  try {
    await createUser({
      username: username.value.trim(),
      email: email.value.trim(),
      password: password.value,
      roles: [role.value]
    })
    toast.ok(`Создан: ${username.value.trim()}`)
    username.value = ''
    email.value = ''
    password.value = ''
    showPassword.value = false
    role.value = 'EDITOR'
    await load()
  } catch (e: any) {
    formError.value = mapCreateError(e)
    toast.error(formError.value)
  } finally {
    saving.value = false
  }
}

async function setRole(user: AdminUser, next: string) {
  try {
    await updateUserRoles(user.id, [next])
    toast.ok(`Роль обновлена: ${user.username}`)
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось сменить роль')
  }
}

function primaryRole(user: AdminUser): string {
  if (user.roles.includes('ADMIN')) return 'ADMIN'
  if (user.roles.includes('MANAGER')) return 'MANAGER'
  if (user.roles.includes('PURCHASER')) return 'PURCHASER'
  return 'EDITOR'
}

async function toggleEnabled(user: AdminUser) {
  const next = !user.enabled
  const label = next ? 'включить' : 'отключить'
  if (!confirm(`${label} «${user.username}»?`)) return
  try {
    await updateUserEnabled(user.id, next)
    toast.ok(next ? 'Включён' : 'Отключён')
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось изменить статус')
  }
}

async function removeUser(user: AdminUser) {
  if (user.id === auth.user?.id) return
  if (!confirm(`Удалить пользователя «${user.username}»? Это действие нельзя отменить.`)) return
  try {
    await deleteUser(user.id)
    toast.ok(`Удалён: ${user.username}`)
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось удалить пользователя')
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-header">
      <div>
        <p class="eyebrow">Доступ</p>
        <h1>Пользователи</h1>
        <p class="muted">Список, роли и блокировка аккаунтов</p>
      </div>
    </header>

    <div class="layout">
      <form class="card form" autocomplete="off" @submit.prevent="submit">
        <h3>Новый пользователь</h3>
        <div class="field">
          <label for="new-user-username">Логин</label>
            <input
            id="new-user-username"
            v-model="username"
            name="new-username"
            type="text"
            autocomplete="off"
            autocapitalize="off"
            spellcheck="false"
            required
            minlength="3"
            maxlength="50"
            readonly
            @focus="unlockAutofill"
            @input="formError = ''"
          />
        </div>
        <div class="field">
          <label for="new-user-email">Email</label>
          <input
            id="new-user-email"
            v-model="email"
            name="new-email"
            type="email"
            inputmode="email"
            placeholder="user@company.com"
            autocomplete="off"
            spellcheck="false"
            required
            readonly
            @focus="unlockAutofill"
            @input="formError = ''"
          />
        </div>
        <div class="field">
          <label for="new-user-password">Пароль</label>
          <div class="password-field">
            <input
              id="new-user-password"
              v-model="password"
              class="password-input"
              name="new-password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="new-password"
              minlength="10"
              maxlength="120"
              pattern="(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{10,}"
              title="Минимум 10 символов: заглавная, строчная буква и цифра"
              required
              spellcheck="false"
              readonly
              @focus="unlockAutofill"
              @input="formError = ''"
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
          <p class="hint">Минимум 10 символов, заглавная, строчная буква и цифра</p>
        </div>
        <div class="field">
          <label for="new-user-role">Роль</label>
          <select id="new-user-role" v-model="role" autocomplete="off">
            <option value="EDITOR">Редактор</option>
            <option value="PURCHASER">Закупщик</option>
            <option value="MANAGER">Менеджер</option>
            <option value="ADMIN">Администратор</option>
          </select>
        </div>
        <p v-if="formError" class="error">{{ formError }}</p>
        <button class="btn" type="submit" :disabled="saving">
          {{ saving ? 'Создаём…' : 'Создать' }}
        </button>
      </form>

      <div class="list-wrap">
        <div class="toolbar card">
          <input v-model="q" placeholder="Поиск…" @keyup.enter="load" />
          <button class="btn secondary" type="button" @click="load">Найти</button>
        </div>
        <p v-if="loading" class="muted">Загрузка…</p>
        <div class="list">
          <article v-for="user in users" :key="user.id" class="card row" :class="{ disabled: !user.enabled }">
            <div>
              <strong>{{ user.username }}</strong>
              <p class="muted">{{ user.email }} · {{ rolesLabel(user.roles as any) }}</p>
            </div>
            <span class="badge" :class="user.enabled ? 'PUBLISHED' : 'ARCHIVED'">
              {{ user.enabled ? 'Активен' : 'Отключён' }}
            </span>
            <div class="actions">
              <select
                :value="primaryRole(user)"
                :disabled="user.id === auth.user?.id"
                @change="setRole(user, ($event.target as HTMLSelectElement).value)"
              >
                <option value="EDITOR">Редактор</option>
                <option value="PURCHASER">Закупщик</option>
                <option value="MANAGER">Менеджер</option>
                <option value="ADMIN">Админ</option>
              </select>
              <button
                class="btn secondary"
                type="button"
                :disabled="user.id === auth.user?.id"
                @click="toggleEnabled(user)"
              >
                {{ user.enabled ? 'Отключить' : 'Включить' }}
              </button>
              <button
                class="btn danger"
                type="button"
                :disabled="user.id === auth.user?.id"
                @click="removeUser(user)"
              >
                Удалить
              </button>
            </div>
          </article>
          <p v-if="!users.length && !loading" class="muted">Никого не найдено</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: minmax(260px, 340px) 1fr;
  gap: 1rem;
  align-items: start;
}
.form {
  padding: 1.15rem;
  display: grid;
  gap: 0.8rem;
}
.form h3 {
  margin: 0;
  font-size: 0.95rem;
}
.password-field {
  position: relative;
}
.password-input {
  width: 100%;
  padding-right: 5.8rem;
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
  cursor: pointer;
}
.toggle:hover {
  background: rgba(141, 198, 63, 0.16);
}
.toolbar {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}
.toolbar input {
  flex: 1;
}
.list {
  display: grid;
  gap: 0.55rem;
}
.row {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.85rem 1rem;
}
.row.disabled {
  opacity: 0.72;
}
.actions {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
  align-items: center;
}
.muted {
  margin: 0.2rem 0 0;
}
.hint {
  margin: 0.2rem 0 0;
  font-size: 0.78rem;
  color: var(--muted);
}
.error {
  margin: 0;
  color: var(--danger);
  font-size: 0.86rem;
}
@media (max-width: 960px) {
  .layout,
  .row {
    grid-template-columns: 1fr;
  }
}
</style>
