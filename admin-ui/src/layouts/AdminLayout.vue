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
    <aside class="sidebar">
      <div class="brand">
        <span class="mark">Б</span>
        <div>
          <strong>Редакция</strong>
          <p>Контент и медиа</p>
        </div>
      </div>
      <nav>
        <RouterLink to="/">Обзор</RouterLink>
        <RouterLink to="/posts">Посты</RouterLink>
        <RouterLink to="/posts/new">Новый пост</RouterLink>
        <RouterLink to="/media">Медиатека</RouterLink>
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
    <main class="content rise">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: 270px 1fr;
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
  border-radius: var(--radius);
  background:
    linear-gradient(165deg, rgba(26, 143, 122, 0.18), transparent 40%),
    var(--sidebar);
  color: var(--sidebar-text);
  box-shadow: var(--shadow);
}

.brand {
  display: flex;
  gap: 0.85rem;
  align-items: center;
  margin-bottom: 1.75rem;
}

.brand .mark {
  width: 2.6rem;
  height: 2.6rem;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: var(--accent-2);
  color: white;
  font-family: var(--font-serif);
  font-weight: 700;
}

.brand strong {
  display: block;
  font-family: var(--font-serif);
  font-size: 1.2rem;
}

.brand p,
.userbox p {
  margin: 0.15rem 0 0;
  color: rgba(232, 242, 238, 0.65);
  font-size: 0.85rem;
}

nav {
  display: grid;
  gap: 0.35rem;
  flex: 1;
}

nav a {
  padding: 0.8rem 0.95rem;
  border-radius: 12px;
  color: rgba(232, 242, 238, 0.72);
  transition: background 0.15s ease, color 0.15s ease;
}

nav a.router-link-active,
nav a:hover {
  background: rgba(255, 255, 255, 0.08);
  color: white;
}

.userbox {
  display: grid;
  gap: 0.75rem;
  padding-top: 1rem;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.userbox .btn.secondary {
  color: white;
  border-color: rgba(255, 255, 255, 0.2);
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
