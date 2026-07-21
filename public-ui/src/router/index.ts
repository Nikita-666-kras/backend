import { createRouter, createWebHistory } from 'vue-router'
import BlogView from '@/views/BlogView.vue'
import PostView from '@/views/PostView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'blog', component: BlogView },
    { path: '/post/:slug', name: 'post', component: PostView, props: true },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ],
  scrollBehavior() {
    return { top: 0 }
  }
})

export default router
