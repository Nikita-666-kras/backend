<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { rolesLabel } from '@/utils/labels'

const auth = useAuthStore()
const toast = useToastStore()
const router = useRouter()
const navOpen = ref(false)

function closeNav() {
  navOpen.value = false
}

async function logout() {
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell" :class="{ 'nav-open': navOpen }">
    <button class="menu-btn" type="button" aria-label="Меню" @click="navOpen = !navOpen">☰</button>
    <div v-if="navOpen" class="scrim" @click="closeNav" />

    <aside class="sidebar">
      <div class="brand">
        <span class="mark">A</span>
        <div>
          <strong>АТРИС</strong>
          <p>Admin · T50</p>
        </div>
      </div>

      <nav @click="closeNav">
        <RouterLink class="nav-link" active-class="" exact-active-class="active" to="/">Обзор</RouterLink>

        <p class="nav-group">Контент</p>
        <RouterLink class="nav-link" active-class="" exact-active-class="active" to="/posts">Посты</RouterLink>
        <RouterLink class="nav-link" active-class="" exact-active-class="active" to="/posts/new">Новый пост</RouterLink>
        <RouterLink class="nav-link media-quick" to="/media?section=ARTICLES">Медиа статей</RouterLink>

        <p class="nav-group">Каталог</p>
        <RouterLink class="nav-link" active-class="" exact-active-class="active" to="/parts">Запчасти</RouterLink>
        <RouterLink class="nav-link" to="/kits">Комплекты</RouterLink>
        <RouterLink class="nav-link" to="/drones">Дроны</RouterLink>
        <RouterLink class="nav-link" to="/categories">Категории</RouterLink>
        <RouterLink class="nav-link" to="/parts-import">Импорт</RouterLink>
        <RouterLink class="nav-link" to="/kp/drone-models">КП · Модели</RouterLink>
        <RouterLink class="nav-link" to="/kp/proposals">КП · Архив</RouterLink>
        <RouterLink class="nav-link media-quick" to="/media?section=PARTS">Медиа запчастей</RouterLink>

        <p class="nav-group">Медиатека</p>
        <RouterLink class="nav-link" active-class="" exact-active-class="active" to="/media">Вся медиатека</RouterLink>

        <template v-if="auth.isAdmin">
          <p class="nav-group">Система</p>
          <RouterLink class="nav-link" to="/users">Пользователи</RouterLink>
        </template>
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
  grid-template-columns: 250px 1fr;
  gap: 1rem;
  min-height: 100vh;
  padding: 1rem;
}

.menu-btn,
.scrim {
  display: none;
}

.sidebar {
  display: flex;
  flex-direction: column;
  padding: 1rem 0.9rem;
  position: sticky;
  top: 1rem;
  height: calc(100vh - 2rem);
  border-radius: 14px;
  border: 1px solid var(--line);
  background: rgba(20, 52, 92, 0.88);
  color: var(--sidebar-text);
}

.brand {
  display: flex;
  gap: 0.7rem;
  align-items: center;
  margin-bottom: 1.1rem;
  padding: 0 0.2rem;
}

.brand .mark {
  width: 2.2rem;
  height: 2.2rem;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--accent);
  color: var(--accent-ink);
  font-weight: 800;
}

.brand strong {
  display: block;
  font-size: 1.05rem;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.brand p,
.userbox p {
  margin: 0.1rem 0 0;
  color: var(--muted);
  font-size: 0.78rem;
}

nav {
  display: grid;
  gap: 0.15rem;
  flex: 1;
  overflow: auto;
}

.nav-group {
  margin: 0.75rem 0 0.2rem;
  padding: 0 0.75rem;
  font-size: 0.65rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #b8c7da;
}

.nav-link {
  padding: 0.55rem 0.75rem;
  border-radius: 10px;
  color: #e8eef6;
}

.nav-link.router-link-active,
.nav-link.active,
.nav-link:hover {
  background: rgba(141, 198, 63, 0.14);
  color: #fff;
}

.nav-link.media-quick {
  color: #b6e86a;
  font-size: 0.88rem;
}

.userbox {
  display: grid;
  gap: 0.6rem;
  padding-top: 0.85rem;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.content {
  min-width: 0;
}

@media (max-width: 960px) {
  .shell {
    grid-template-columns: 1fr;
    padding-top: 3.2rem;
  }

  .menu-btn {
    display: grid;
    place-items: center;
    position: fixed;
    z-index: 40;
    top: 0.75rem;
    left: 0.75rem;
    width: 2.5rem;
    height: 2.5rem;
    border-radius: 10px;
    border: 1px solid var(--line);
    background: rgba(20, 52, 92, 0.95);
    color: #fff;
    font-size: 1.2rem;
  }

  .scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 35;
    background: rgba(0, 0, 0, 0.45);
  }

  .sidebar {
    position: fixed;
    z-index: 45;
    top: 0;
    left: 0;
    height: 100vh;
    width: min(280px, 86vw);
    border-radius: 0;
    transform: translateX(-105%);
    transition: transform 0.18s ease;
  }

  .shell.nav-open .sidebar {
    transform: translateX(0);
  }
}
</style>
