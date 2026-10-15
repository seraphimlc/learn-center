<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import http from '@/api/http'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const unread = ref(0)
const q = ref(route.query.keyword || '')
const menuOpen = ref(false)
const headerRef = ref(null)

const links = [
  { to: '/', label: '首页' },
  { to: '/courses', label: '课程中心' },
  { to: '/news', label: '新闻资讯' },
  { to: '/about', label: '关于我们' },
  { to: '/learning', label: '我的学习', auth: true },
]

function active(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

async function loadUnread() {
  if (!auth.isLogin) return
  try {
    const res = await http.get('/notifications/unread-count')
    unread.value = res.data.count || 0
  } catch {}
}

function search() {
  menuOpen.value = false
  router.push({ path: '/courses', query: { keyword: q.value || undefined } })
}

function logout() {
  menuOpen.value = false
  auth.logout()
  router.push('/')
}

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function closeMenu() {
  menuOpen.value = false
}

function onDocClick(e) {
  if (!menuOpen.value) return
  if (headerRef.value && !headerRef.value.contains(e.target)) {
    menuOpen.value = false
  }
}

function onKeydown(e) {
  if (e.key === 'Escape') menuOpen.value = false
}

watch(() => route.fullPath, () => { menuOpen.value = false })

onMounted(() => {
  loadUnread()
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <header class="header" ref="headerRef">
    <div class="container bar">
      <RouterLink to="/" class="brand" @click="closeMenu">
        <span class="logo">知</span>
        <div>
          <strong>知行学堂</strong>
          <small>企业视频学习平台</small>
        </div>
      </RouterLink>
      <nav class="nav desktop-only">
        <RouterLink v-for="l in links" :key="l.to" :to="l.to"
          v-show="!l.auth || auth.isLogin"
          :class="{ active: active(l.to) }">{{ l.label }}</RouterLink>
      </nav>
      <div class="actions">
        <form class="search desktop-only" @submit.prevent="search">
          <input v-model="q" placeholder="搜索课程…" />
          <button type="submit">搜索</button>
        </form>
        <template v-if="auth.isLogin">
          <RouterLink to="/notifications" class="icon-btn desktop-only" title="消息">
            🔔<span v-if="unread" class="dot">{{ unread > 9 ? '9+' : unread }}</span>
          </RouterLink>
          <RouterLink to="/ai" class="chip desktop-only">AI 学情</RouterLink>
          <RouterLink to="/profile" class="user desktop-only">{{ auth.user?.nickname || auth.user?.username }}</RouterLink>
          <button class="btn btn-ghost desktop-only logout-btn" @click="logout">退出</button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="btn btn-ghost desktop-only auth-btn">登录</RouterLink>
          <RouterLink to="/register" class="btn btn-primary desktop-only auth-btn">注册</RouterLink>
        </template>
        <button
          type="button"
          class="hamburger"
          :aria-expanded="menuOpen"
          aria-label="菜单"
          @click.stop="toggleMenu"
        >
          <span :class="['ham-line', { open: menuOpen }]" />
          <span :class="['ham-line', { open: menuOpen }]" />
          <span :class="['ham-line', { open: menuOpen }]" />
        </button>
      </div>
    </div>

    <div v-show="menuOpen" class="mobile-panel" @click.stop>
      <nav class="mobile-nav">
        <RouterLink v-for="l in links" :key="'m'+l.to" :to="l.to"
          v-show="!l.auth || auth.isLogin"
          :class="{ active: active(l.to) }"
          @click="closeMenu">{{ l.label }}</RouterLink>
      </nav>
      <form class="mobile-search" @submit.prevent="search">
        <input v-model="q" placeholder="搜索课程…" />
        <button type="submit">搜索</button>
      </form>
      <div class="mobile-actions">
        <template v-if="auth.isLogin">
          <RouterLink to="/notifications" class="mobile-link" @click="closeMenu">
            消息通知
            <span v-if="unread" class="badge-count">{{ unread > 9 ? '9+' : unread }}</span>
          </RouterLink>
          <RouterLink to="/ai" class="mobile-link" @click="closeMenu">AI 学情</RouterLink>
          <RouterLink to="/profile" class="mobile-link" @click="closeMenu">
            {{ auth.user?.nickname || auth.user?.username || '个人中心' }}
          </RouterLink>
          <button type="button" class="mobile-logout" @click="logout">退出登录</button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="btn btn-ghost mobile-auth" @click="closeMenu">登录</RouterLink>
          <RouterLink to="/register" class="btn btn-primary mobile-auth" @click="closeMenu">注册</RouterLink>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: sticky; top: 0; z-index: 50;
  backdrop-filter: blur(14px);
  background: rgba(255,255,255,.86);
  border-bottom: 1px solid rgba(15,23,42,.06);
}
.bar { display:flex; align-items:center; gap: 20px; min-height: 68px; }
.brand { display:flex; align-items:center; gap: 10px; flex-shrink: 0; }
.logo {
  width: 40px; height: 40px; border-radius: 12px; display:grid; place-items:center;
  background: linear-gradient(135deg, var(--brand-600), var(--brand-900)); color:#fff; font-weight:700;
}
.brand strong { display:block; font-size: 16px; letter-spacing: -.02em; }
.brand small { color: var(--ink-500); font-size: 11px; }
.nav { display:flex; gap: 6px; flex:1; }
.nav a {
  padding: 8px 12px; border-radius: 999px; color: var(--ink-700); font-weight: 500; font-size: 14px;
}
.nav a.active, .nav a:hover { background: var(--brand-50); color: var(--brand-800); }
.actions { display:flex; align-items:center; gap: 10px; margin-left: auto; }
.search { display:flex; background:#fff; border:1px solid var(--ink-300); border-radius:999px; overflow:hidden; }
.search input { border:none; outline:none; padding: 8px 12px; width: 160px; background:transparent; }
.search button {
  border:none; background: var(--ink-900); color:#fff; padding: 8px 12px; cursor:pointer; font-weight:600;
}
.icon-btn { position:relative; font-size: 18px; padding: 6px; }
.dot {
  position:absolute; top:-2px; right:-6px; background: var(--danger); color:#fff;
  font-size: 10px; border-radius: 999px; padding: 1px 5px;
}
.chip {
  background: linear-gradient(135deg, #ecfeff, #fef3c7); color: var(--brand-800);
  padding: 6px 10px; border-radius: 999px; font-size: 12px; font-weight: 700;
}
.user { font-weight: 600; color: var(--ink-700); font-size: 14px; }
.logout-btn { padding:8px 12px; }
.auth-btn { padding:8px 14px; }

.hamburger {
  display: none;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  width: 42px; height: 42px;
  padding: 10px;
  border: 1px solid var(--ink-300);
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  flex-shrink: 0;
}
.ham-line {
  display: block;
  height: 2px;
  width: 100%;
  background: var(--ink-900);
  border-radius: 2px;
  transition: transform .2s ease, opacity .2s ease;
}
.ham-line.open:nth-child(1) { transform: translateY(7px) rotate(45deg); }
.ham-line.open:nth-child(2) { opacity: 0; }
.ham-line.open:nth-child(3) { transform: translateY(-7px) rotate(-45deg); }

.mobile-panel {
  display: none;
  border-top: 1px solid rgba(15,23,42,.06);
  background: rgba(255,255,255,.98);
  padding: 12px 16px 18px;
  box-shadow: 0 16px 32px rgba(15,23,42,.08);
}
.mobile-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.mobile-nav a {
  padding: 12px 14px;
  border-radius: 12px;
  font-weight: 600;
  font-size: 15px;
  color: var(--ink-700);
}
.mobile-nav a.active, .mobile-nav a:hover {
  background: var(--brand-50);
  color: var(--brand-800);
}
.mobile-search {
  display: flex;
  margin-top: 12px;
  background: #fff;
  border: 1px solid var(--ink-300);
  border-radius: 999px;
  overflow: hidden;
}
.mobile-search input {
  flex: 1;
  border: none;
  outline: none;
  padding: 10px 14px;
  min-width: 0;
  background: transparent;
  font-size: 15px;
}
.mobile-search button {
  border: none;
  background: var(--ink-900);
  color: #fff;
  padding: 10px 16px;
  cursor: pointer;
  font-weight: 600;
  flex-shrink: 0;
}
.mobile-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--ink-100);
}
.mobile-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-radius: 12px;
  font-weight: 600;
  font-size: 15px;
  color: var(--ink-700);
}
.mobile-link:hover { background: var(--brand-50); color: var(--brand-800); }
.badge-count {
  background: var(--danger);
  color: #fff;
  font-size: 11px;
  border-radius: 999px;
  padding: 2px 7px;
}
.mobile-logout {
  margin-top: 4px;
  padding: 12px 14px;
  border: 1px solid var(--ink-300);
  border-radius: 12px;
  background: #fff;
  font-weight: 600;
  font-size: 15px;
  color: var(--ink-700);
  cursor: pointer;
  text-align: left;
}
.mobile-auth {
  display: flex;
  justify-content: center;
  padding: 12px 14px !important;
  width: 100%;
}

@media (max-width: 960px) {
  .desktop-only { display: none !important; }
  .hamburger { display: flex; }
  .mobile-panel { display: block; }
  .bar { gap: 12px; min-height: 60px; }
  .brand small { display: none; }
}
</style>
