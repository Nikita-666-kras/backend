import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layouts/ManagerLayout.vue'),
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
        { path: 'kp', name: 'kp-list', component: () => import('@/views/kp/KpListView.vue') },
        { path: 'kp/new', name: 'kp-new', component: () => import('@/views/kp/KpEditorView.vue') },
        { path: 'kp/:id', name: 'kp-edit', component: () => import('@/views/kp/KpEditorView.vue') },
        { path: 'clients', name: 'clients', component: () => import('@/views/hub/PlaceholderView.vue'), props: { title: 'Клиенты', description: 'База клиентов и получателей КП' } },
        { path: 'leads', name: 'leads', component: () => import('@/views/hub/PlaceholderView.vue'), props: { title: 'Заявки', description: 'Входящие заявки и лиды' } },
        { path: 'orders', name: 'orders', component: () => import('@/views/hub/PlaceholderView.vue'), props: { title: 'Заказы', description: 'Заказы и отгрузки' } }
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
  return true
})

export default router
