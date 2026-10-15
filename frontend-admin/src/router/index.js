
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const ALL = ['ADMIN', 'CONTENT_ADMIN', 'DATA_VIEWER']
const WRITE = ['ADMIN', 'CONTENT_ADMIN']
const SUPER = ['ADMIN']
const DATA = ['ADMIN', 'DATA_VIEWER']

const routes = [
  { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      { path: '', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { roles: ALL } },
      { path: 'users', component: () => import('@/views/UsersView.vue'), meta: { roles: SUPER } },
      { path: 'news', component: () => import('@/views/NewsView.vue'), meta: { roles: WRITE } },
      { path: 'categories', component: () => import('@/views/CategoriesView.vue'), meta: { roles: WRITE } },
      { path: 'courses', component: () => import('@/views/CoursesView.vue'), meta: { roles: WRITE } },
      { path: 'courses/:id/lessons', component: () => import('@/views/LessonsView.vue'), meta: { roles: WRITE } },
      { path: 'banners', component: () => import('@/views/BannersView.vue'), meta: { roles: WRITE } },
      { path: 'configs', component: () => import('@/views/ConfigsView.vue'), meta: { roles: SUPER } },
      { path: 'announce', component: () => import('@/views/AnnounceView.vue'), meta: { roles: WRITE } },
      { path: 'audit', component: () => import('@/views/AuditView.vue'), meta: { roles: ALL } },
      { path: 'certs', component: () => import('@/views/CertsView.vue'), meta: { roles: ALL } },
      { path: 'import-export', component: () => import('@/views/ImportExportView.vue'), meta: { roles: DATA } },
    ]
  }
]

const router = createRouter({ history: createWebHistory('/admin/'), routes })
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLogin) return '/login'
  if (to.path === '/login' && auth.isLogin) return '/'
  const roles = to.meta.roles
  if (roles && auth.isLogin && !roles.includes(auth.role)) {
    return '/'
  }
})
export default router
