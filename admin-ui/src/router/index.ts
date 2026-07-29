import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { canAccessMediaSection, defaultMediaSection, normalizeMediaSectionQuery } from '@/utils/mediaSections'
import { defaultAdminRoute, redirectToManagerHub } from '@/utils/roles'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { editor: true, catalog: true } },
        { path: 'posts', name: 'posts', component: () => import('@/views/PostsView.vue'), meta: { editor: true } },
        { path: 'posts/new', name: 'post-new', component: () => import('@/views/PostEditorView.vue'), meta: { editor: true } },
        { path: 'posts/:id', name: 'post-edit', component: () => import('@/views/PostEditorView.vue'), meta: { editor: true } },
        { path: 'parts', name: 'parts', component: () => import('@/views/parts/PartsCatalogView.vue'), meta: { catalog: true } },
        { path: 'parts/new', name: 'part-new', component: () => import('@/views/parts/PartEditorView.vue'), meta: { catalog: true } },
        { path: 'parts/:id', name: 'part-edit', component: () => import('@/views/parts/PartEditorView.vue'), meta: { catalog: true } },
        { path: 'kits', name: 'kits', component: () => import('@/views/parts/KitsView.vue'), meta: { catalog: true } },
        { path: 'kits/new', name: 'kit-new', component: () => import('@/views/parts/KitsView.vue'), meta: { catalog: true } },
        { path: 'kits/:id', name: 'kit-edit', component: () => import('@/views/parts/KitsView.vue'), meta: { catalog: true } },
        { path: 'drones', name: 'drones', component: () => import('@/views/parts/DronesView.vue'), meta: { catalog: true } },
        { path: 'categories', name: 'categories', component: () => import('@/views/parts/CategoriesView.vue'), meta: { catalog: true } },
        { path: 'parts-import', name: 'parts-import', component: () => import('@/views/parts/ImportView.vue'), meta: { catalog: true } },
        { path: 'kp/drone-models', name: 'kp-drone-models', component: () => import('@/views/kp/DroneModelsView.vue'), meta: { admin: true } },
        { path: 'kp/proposals', name: 'kp-proposals', component: () => import('@/views/kp/ProposalsView.vue'), meta: { admin: true } },
        { path: 'media', name: 'media', component: () => import('@/views/MediaLibraryView.vue'), meta: { media: true } },
        { path: 'users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { admin: true } }
      ]
    }
  ]
})

function homeRoute(auth: ReturnType<typeof useAuthStore>) {
  const route = defaultAdminRoute(auth.user?.roles)
  if (route.includes('?')) {
    const [path, query] = route.split('?')
    const params = new URLSearchParams(query)
    const section = params.get('section')
    return section ? { path, query: { section } } : path
  }
  return route
}

function mediaGuard(auth: ReturnType<typeof useAuthStore>, to: { query: Record<string, unknown> }) {
  const raw = typeof to.query.section === 'string' ? to.query.section : null
  const section = normalizeMediaSectionQuery(auth.user?.roles, raw)
  if (raw !== section) {
    return { name: 'media', query: { section } }
  }
  if (!canAccessMediaSection(auth.user?.roles, section)) {
    return { name: 'media', query: { section: defaultMediaSection(auth.user?.roles) } }
  }
  return true
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    if (auth.isAuthenticated) {
      if (!auth.user) {
        try {
          await auth.fetchMe()
        } catch {
          auth.clearSession()
          return true
        }
      }
      if (auth.isManagerOnly && auth.canUseManagerUi) {
        redirectToManagerHub()
        return false
      }
      if (!auth.canUseAdminUi) {
        auth.clearSession()
        return { name: 'login', query: { error: 'forbidden' } }
      }
      return homeRoute(auth)
    }
    return true
  }

  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      auth.clearSession()
      return { name: 'login' }
    }
  }

  if (auth.isManagerOnly && auth.canUseManagerUi && to.name !== 'media') {
    return { name: 'media', query: { section: 'OTHER' } }
  }

  if (!auth.canUseAdminUi) {
    auth.clearSession()
    return { name: 'login', query: { error: 'forbidden' } }
  }

  if (to.meta.admin && !auth.isAdmin) {
    return homeRoute(auth)
  }

  if (auth.isEditorOnly && !to.meta.editor && !to.meta.media) {
    return { name: 'posts' }
  }

  if (auth.isPurchaserOnly) {
    if (!to.meta.catalog && !to.meta.media && to.name !== 'dashboard') {
      return { name: 'parts' }
    }
  }

  if (to.meta.media) {
    const result = mediaGuard(auth, to)
    if (result !== true) return result
  }

  return true
})

export default router
