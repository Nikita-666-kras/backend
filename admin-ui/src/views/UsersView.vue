<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  createUser,
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

/** Prevent password-manager autofill from locking/styling create-user fields. */
function unlockAutofill(event: Event) {
  const el = event.target as HTMLInputElement
  el.removeAttribute('readonly')
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
  try {
    await createUser({
      username: username.value,
      email: email.value,
      password: password.value,
      roles: [role.value]
    })
    toast.ok(`Создан: ${username.value}`)
    username.value = ''
    email.value = ''
    password.value = ''
    showPassword.value = false
    role.value = 'EDITOR'
    await load()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Не удалось создать пользователя')
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
            readonly
            @focus="unlockAutofill"
          />
        </div>
        <div class="field">
          <label for="new-user-email">Email</label>
          <input
            id="new-user-email"
            v-model="email"
            name="new-email"
            type="email"
            autocomplete="off"
            spellcheck="false"
            required
            readonly
            @focus="unlockAutofill"
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
              required
              spellcheck="false"
              readonly
              @focus="unlockAutofill"
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
        <div class="field">
          <label for="new-user-role">Роль</label>
          <select id="new-user-role" v-model="role" autocomplete="off">
            <option value="EDITOR">Редактор</option>
            <option value="MANAGER">Менеджер</option>
            <option value="ADMIN">Администратор</option>
          </select>
        </div>
        <button class="btn" type="submit">Создать</button>
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
                :value="user.roles.includes('ADMIN') ? 'ADMIN' : 'EDITOR'"
                :disabled="user.id === auth.user?.id"
                @change="setRole(user, ($event.target as HTMLSelectElement).value)"
              >
                <option value="EDITOR">Редактор</option>
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
@media (max-width: 960px) {
  .layout,
  .row {
    grid-template-columns: 1fr;
  }
}
</style>
