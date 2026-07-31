<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { hubGroupLabels, hubModules } from '@/config/modules'
import { useAuthStore } from '@/stores/auth'
import { rolesLabel } from '@/utils/labels'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const navOpen = ref(false)

const groups = computed(() => {
  const order: Array<'main' | 'kp'> = ['main', 'kp']
  return order.map((group) => ({
    group,
    label: hubGroupLabels[group],
    items: hubModules.filter((m) => m.group === group && m.enabled)
  }))
})

function isActive(itemRoute: string) {
  if (itemRoute === '/') return route.path === '/'
  if (itemRoute === '/kp') return route.path === '/kp'
  return route.path === itemRoute || route.path.startsWith(itemRoute + '/')
}

async function logout() {
  await auth.logout()
  router.push({ name: 'login' })
}

function closeNav() {
  navOpen.value = false
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
          <p>Менеджерский хаб</p>
        </div>
      </div>

      <nav @click="closeNav">
        <template v-for="section in groups" :key="section.group">
          <p class="nav-group">{{ section.label }}</p>
          <RouterLink
            v-for="item in section.items"
            :key="item.id"
            class="nav-link"
            :class="{ active: isActive(item.route) }"
            :to="item.route"
          >{{ item.title }}</RouterLink>
        </template>
      </nav>

      <div class="userbox">
        <div>
          <strong>{{ auth.user?.username }}</strong>
          <p class="muted">{{ rolesLabel(auth.user?.roles) }}</p>
        </div>
        <button class="btn secondary" type="button" @click="logout">Выйти</button>
      </div>
    </aside>

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell { display: grid; grid-template-columns: 250px 1fr; gap: 1rem; min-height: 100vh; padding: 1rem; }
.menu-btn, .scrim { display: none; }
.sidebar { display: flex; flex-direction: column; padding: 1rem 0.9rem; position: sticky; top: 1rem; height: calc(100vh - 2rem); border-radius: 14px; border: 1px solid var(--line); background: rgba(20, 52, 92, 0.88); color: var(--sidebar-text); }
.brand { display: flex; gap: 0.7rem; align-items: center; margin-bottom: 1.1rem; }
.brand .mark { width: 2.2rem; height: 2.2rem; border-radius: 10px; display: grid; place-items: center; background: var(--accent); color: var(--accent-ink); font-weight: 800; }
.brand strong { display: block; font-size: 1.05rem; font-weight: 800; }
.brand p { margin: 0.1rem 0 0; color: var(--muted); font-size: 0.78rem; }
nav { display: grid; gap: 0.15rem; flex: 1; overflow: auto; }
.nav-group { margin: 0.75rem 0 0.2rem; padding: 0 0.75rem; font-size: 0.65rem; letter-spacing: 0.1em; text-transform: uppercase; color: #b8c7da; }
.nav-link { padding: 0.55rem 0.75rem; border-radius: 10px; color: #e8eef6; display: flex; align-items: center; gap: 0.4rem; }
.nav-link.active, .nav-link:hover { background: rgba(141, 198, 63, 0.14); color: #fff; }
.nav-link.disabled { opacity: 0.55; cursor: default; }
.nav-link.external { color: #b6e86a; font-size: 0.88rem; }
.nav-badge { font-size: 0.65rem; padding: 0.1rem 0.4rem; border-radius: 999px; background: rgba(255, 255, 255, 0.1); color: var(--muted); }
.userbox { display: grid; gap: 0.6rem; padding-top: 0.85rem; border-top: 1px solid rgba(255, 255, 255, 0.1); }
.content { min-width: 0; }
@media (max-width: 1200px) {
  .shell { grid-template-columns: 220px 1fr; gap: 0.8rem; }
  .sidebar { padding: 0.85rem 0.75rem; }
}
@media (max-width: 960px) {
  .shell { grid-template-columns: 1fr; padding-top: 3.2rem; }
  .menu-btn { display: grid; place-items: center; position: fixed; z-index: 40; top: 0.75rem; left: 0.75rem; width: 2.5rem; height: 2.5rem; border-radius: 10px; border: 1px solid var(--line); background: rgba(20, 52, 92, 0.95); color: #fff; font-size: 1.2rem; }
  .scrim { display: block; position: fixed; inset: 0; z-index: 35; background: rgba(0, 0, 0, 0.45); }
  .sidebar { position: fixed; z-index: 45; top: 0; left: 0; height: 100vh; width: min(280px, 86vw); border-radius: 0; transform: translateX(-105%); transition: transform 0.18s ease; }
  .shell.nav-open .sidebar { transform: translateX(0); }
}
</style>
