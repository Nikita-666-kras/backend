<script setup lang="ts">
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { rolesLabel } from '@/utils/labels'

const auth = useAuthStore()
const router = useRouter()

async function logout() {
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar card">
      <div class="brand">
        <span class="mark">Б</span>
        <div>
          <strong>Редакция</strong>
          <p>Админка блога</p>
        </div>
      </div>
      <nav>
        <RouterLink to="/">Обзор</RouterLink>
        <RouterLink to="/posts">Посты</RouterLink>
        <RouterLink to="/posts/new">Новый пост</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/users">Пользователи</RouterLink>
      </nav>
      <div class="userbox">
        <div>
          <strong>{{ auth.user?.username }}</strong>
          <p>{{ rolesLabel(auth.user?.roles) }}</p>
        </div>
        <button class="btn secondary" @click="logout">Выйти</button>
      </div>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 1.25rem;
  min-height: 100vh;
  padding: 1.25rem;
}

.sidebar {
  display: flex;
  flex-direction: column;
  padding: 1.25rem;
  position: sticky;
  top: 1.25rem;
  height: calc(100vh - 2.5rem);
}

.brand {
  display: flex;
  gap: 0.85rem;
  align-items: center;
  margin-bottom: 1.75rem;
}

.brand .mark {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: var(--accent);
  color: white;
  font-family: var(--font-serif);
  font-weight: 700;
}

.brand strong {
  display: block;
  font-family: var(--font-serif);
  font-size: 1.15rem;
}

.brand p,
.userbox p {
  margin: 0.15rem 0 0;
  color: var(--muted);
  font-size: 0.85rem;
}

nav {
  display: grid;
  gap: 0.35rem;
  flex: 1;
}

nav a {
  padding: 0.75rem 0.9rem;
  border-radius: 10px;
  color: var(--muted);
}

nav a.router-link-active,
nav a:hover {
  background: var(--accent-soft);
  color: var(--accent);
}

.userbox {
  display: grid;
  gap: 0.75rem;
  padding-top: 1rem;
  border-top: 1px solid var(--line);
}

.content {
  min-width: 0;
}

@media (max-width: 960px) {
  .shell {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: static;
    height: auto;
  }
}
</style>
