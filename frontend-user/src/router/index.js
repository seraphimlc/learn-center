
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { guest: true } },
  { path: '/register', name: 'register', component: () => import('@/views/RegisterView.vue'), meta: { guest: true } },
  { path: '/forgot-password', name: 'forgot', component: () => import('@/views/ForgotPasswordView.vue'), meta: { guest: true } },
  { path: '/login/wechat/callback', name: 'wechat-callback', component: () => import('@/views/WechatCallbackView.vue'), meta: { guest: true } },
  { path: '/about', name: 'about', component: () => import('@/views/AboutView.vue') },
  { path: '/news', name: 'news', component: () => import('@/views/NewsListView.vue') },
  { path: '/news/:id', name: 'news-detail', component: () => import('@/views/NewsDetailView.vue') },
  { path: '/courses', name: 'courses', component: () => import('@/views/CourseListView.vue') },
  { path: '/courses/:id', name: 'course-detail', component: () => import('@/views/CourseDetailView.vue') },
  { path: '/play/:courseId/:lessonId', name: 'play', component: () => import('@/views/PlayerView.vue'), meta: { auth: true } },
  { path: '/learning', name: 'learning', component: () => import('@/views/MyLearningView.vue'), meta: { auth: true } },
  { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { auth: true } },
  { path: '/notifications', name: 'notifications', component: () => import('@/views/NotificationsView.vue'), meta: { auth: true } },
  { path: '/certs', name: 'certs', component: () => import('@/views/CertsView.vue'), meta: { auth: true } },
  { path: '/ai', name: 'ai', component: () => import('@/views/AiView.vue'), meta: { auth: true } },
]

const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.auth && !auth.isLogin) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.guest && auth.isLogin) return { name: 'home' }
})

export default router
