<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const active = computed(() => route.path)

/** roles: all staff | content writers | super-admin only */
const menus = [
  { path: '/', label: '数据看板', icon: 'DataAnalysis', roles: ['ADMIN', 'CONTENT_ADMIN', 'DATA_VIEWER'] },
  { path: '/users', label: '用户管理', icon: 'User', roles: ['ADMIN'] },
  { path: '/news', label: '新闻管理', icon: 'Notebook', roles: ['ADMIN', 'CONTENT_ADMIN'] },
  { path: '/categories', label: '分类管理', icon: 'Menu', roles: ['ADMIN', 'CONTENT_ADMIN'] },
  { path: '/courses', label: '课程管理', icon: 'VideoCamera', roles: ['ADMIN', 'CONTENT_ADMIN'] },
  { path: '/banners', label: '轮播配置', icon: 'Picture', roles: ['ADMIN', 'CONTENT_ADMIN'] },
  { path: '/announce', label: '公告推送', icon: 'Bell', roles: ['ADMIN', 'CONTENT_ADMIN'] },
  { path: '/configs', label: '运营配置', icon: 'Setting', roles: ['ADMIN'] },
  { path: '/certs', label: '证书记录', icon: 'Medal', roles: ['ADMIN', 'CONTENT_ADMIN', 'DATA_VIEWER'] },
  { path: '/import-export', label: '导入导出', icon: 'Upload', roles: ['ADMIN', 'DATA_VIEWER'] },
  { path: '/audit', label: '操作日志', icon: 'Document', roles: ['ADMIN', 'CONTENT_ADMIN', 'DATA_VIEWER'] },
]

const visibleMenus = computed(() => {
  const r = auth.role
  return menus.filter(m => m.roles.includes(r))
})

const roleLabel = computed(() => {
  switch (auth.role) {
    case 'ADMIN': return '超级管理员'
    case 'CONTENT_ADMIN': return '内容管理员'
    case 'DATA_VIEWER': return '数据查看员'
    default: return auth.role || '—'
  }
})

const roleTagType = computed(() => {
  if (auth.role === 'ADMIN') return 'danger'
  if (auth.role === 'CONTENT_ADMIN') return 'warning'
  return 'info'
})

function go(path) { router.push(path) }
function logout() { auth.logout(); router.push('/login') }
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-aside">
      <div class="brand">
        <div class="mark">管</div>
        <div><strong>知行学堂</strong><small>Admin Console</small></div>
      </div>
      <el-menu :default-active="active" background-color="transparent" text-color="#cbd5e1" active-text-color="#5eead4" @select="go">
        <el-menu-item v-for="m in visibleMenus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>
    <div class="admin-main">
      <header class="admin-top">
        <div style="font-weight:600;color:#334155">内容运营与学习数据管理</div>
        <div style="display:flex;align-items:center;gap:12px">
          <el-tag :type="roleTagType" effect="dark" round>{{ roleLabel }}</el-tag>
          <span>{{ auth.user?.nickname || auth.user?.username }}</span>
          <el-button link type="primary" @click="logout">退出</el-button>
        </div>
      </header>
      <div class="admin-content"><router-view /></div>
    </div>
  </div>
</template>
