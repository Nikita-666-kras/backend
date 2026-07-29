import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

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
        { path: '', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
        { path: 'posts', name: 'posts', component: () => import('@/views/PostsView.vue') },
        { path: 'posts/new', name: 'post-new', component: () => import('@/views/PostEditorView.vue') },
        { path: 'posts/:id', name: 'post-edit', component: () => import('@/views/PostEditorView.vue') },
        { path: 'parts', name: 'parts', component: () => import('@/views/parts/PartsCatalogView.vue') },
        { path: 'parts/new', name: 'part-new', component: () => import('@/views/parts/PartEditorView.vue') },
        { path: 'parts/:id', name: 'part-edit', component: () => import('@/views/parts/PartEditorView.vue') },
        { path: 'kits', name: 'kits', component: () => import('@/views/parts/KitsView.vue') },
        { path: 'kits/new', name: 'kit-new', component: () => import('@/views/parts/KitsView.vue') },
        { path: 'kits/:id', name: 'kit-edit', component: () => import('@/views/parts/KitsView.vue') },
        { path: 'drones', name: 'drones', component: () => import('@/views/parts/DronesView.vue') },
        { path: 'categories', name: 'categories', component: () => import('@/views/parts/CategoriesView.vue') },
        { path: 'parts-import', name: 'parts-import', component: () => import('@/views/parts/ImportView.vue') },
        { path: 'kp/drone-models', name: 'kp-drone-models', component: () => import('@/views/kp/DroneModelsView.vue'), meta: { admin: true } },
        { path: 'kp/proposals', name: 'kp-proposals', component: () => import('@/views/kp/ProposalsView.vue'), meta: { admin: true } },
        { path: 'media', name: 'media', component: () => import('@/views/MediaLibraryView.vue') },
        { path: 'users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { admin: true } }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.isAuthenticated) return { name: 'dashboard' }
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
  if (to.meta.admin && !auth.isAdmin) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
