<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import http from '@/api/http'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const message = ref('正在完成微信登录…')
const failed = ref(false)

onMounted(async () => {
  // Support token handed off via query/hash (backend redirect style)
  const hash = new URLSearchParams((location.hash || '').replace(/^#/, ''))
  const tokenFromQuery = route.query.token || hash.get('token')
  if (tokenFromQuery) {
    try {
      auth.applyAuth({
        token: String(tokenFromQuery),
        userId: route.query.userId || hash.get('userId'),
        username: route.query.username || hash.get('username'),
        nickname: route.query.nickname || hash.get('nickname'),
        role: route.query.role || hash.get('role') || 'USER',
        avatar: route.query.avatar || hash.get('avatar') || '',
      })
      toast('微信登录成功')
      router.replace(route.query.redirect || '/')
      return
    } catch (e) {
      failed.value = true
      message.value = e.message || '登录失败'
      return
    }
  }

  const code = route.query.code
  const state = route.query.state
  if (!code || !state) {
    failed.value = true
    message.value = route.query.message || '微信授权未完成或已取消'
    return
  }
  try {
    const res = await http.get('/auth/wechat/callback', { params: { code, state } })
    auth.applyAuth(res.data)
    toast('微信登录成功')
    router.replace(route.query.redirect || '/')
  } catch (e) {
    failed.value = true
    message.value = e.message || '微信登录失败'
    toast(message.value, 'error')
  }
})
</script>

<template>
  <div class="page">
    <div class="auth card">
      <div class="head">
        <h1>微信登录</h1>
        <p>{{ message }}</p>
      </div>
      <div v-if="failed" class="foot">
        <RouterLink to="/login">返回登录</RouterLink>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth { width: min(420px, 100% - 32px); margin: 40px auto; padding: 32px; }
.head h1 { margin:0 0 6px; }
.head p { margin:0; color: var(--ink-500); }
.foot { margin-top: 18px; font-size:13px; }
.foot a { color: var(--brand-700); font-weight:600; }
</style>
