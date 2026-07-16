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
      auth.logout()
      return { name: 'login' }
    }
  }
  if (to.meta.admin && !auth.isAdmin) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
