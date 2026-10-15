<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import http from '@/api/http'
import { toast } from '@/utils/toast'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const form = ref({ username: '', password: '' })
const loading = ref(false)
const wechatLoading = ref(false)
const wechatEnabled = ref(false)

onMounted(async () => {
  try {
    const res = await http.get('/auth/wechat/status')
    wechatEnabled.value = !!(res.data && res.data.enabled)
  } catch {
    wechatEnabled.value = false
  }
})

async function submit() {
  loading.value = true
  try {
    await auth.login(form.value)
    toast('登录成功')
    router.replace(route.query.redirect || '/')
  } catch (e) {
    toast(e.message, 'error')
  } finally { loading.value = false }
}

async function wechatLogin() {
  wechatLoading.value = true
  try {
    const res = await http.get('/auth/wechat/qr-url')
    const url = res.data?.authorizeUrl
    if (!url) throw new Error('未获取到微信授权地址')
    // Redirect to WeChat QR page (open platform website app)
    location.href = url
  } catch (e) {
    toast(e.message || '微信登录暂不可用', 'error')
    wechatLoading.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="auth card">
      <div class="head">
        <h1>欢迎回来</h1>
        <p>登录知行学堂，继续你的学习旅程</p>
      </div>
      <form @submit.prevent="submit" class="form">
        <label class="label">用户名</label>
        <input class="input" v-model="form.username" required placeholder="请输入用户名" />
        <label class="label" style="margin-top:12px">密码</label>
        <input class="input" type="password" v-model="form.password" required placeholder="请输入密码" />
        <button class="btn btn-primary" style="width:100%;margin-top:18px" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>

      <template v-if="wechatEnabled">
        <div class="divider"><span>或</span></div>
        <button class="btn btn-wechat" type="button" :disabled="wechatLoading" @click="wechatLogin">
          {{ wechatLoading ? '跳转中…' : '微信登录' }}
        </button>
        <p class="hint">使用微信扫码登录（开放平台网站应用）</p>
      </template>
      <p v-else class="hint muted">也可使用账号密码登录；微信登录需管理员配置开放平台凭证后启用。</p>

      <div class="foot">
        <div>
          <RouterLink to="/register">没有账号？去注册</RouterLink>
          <span style="margin:0 8px;color:#cbd5e1">·</span>
          <RouterLink to="/forgot-password">忘记密码</RouterLink>
        </div>
        <span>演示学员 demo / Demo@123</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth { width: min(420px, 100% - 32px); margin: 40px auto; padding: 32px; }
.head h1 { margin:0 0 6px; letter-spacing:-.02em; }
.head p { margin:0 0 22px; color: var(--ink-500); }
.foot { margin-top: 18px; display:flex; justify-content:space-between; gap:12px; font-size:13px; color:var(--ink-500); }
.foot a { color: var(--brand-700); font-weight:600; }
.divider {
  display:flex; align-items:center; gap:12px; margin:20px 0 14px; color:#94a3b8; font-size:12px;
}
.divider::before, .divider::after {
  content:''; flex:1; height:1px; background:#e2e8f0;
}
.btn-wechat {
  width:100%;
  background:#07c160;
  color:#fff;
  border:none;
  border-radius:10px;
  padding:11px 16px;
  font-weight:600;
  font-size:15px;
  cursor:pointer;
}
.btn-wechat:disabled { opacity:.7; cursor:not-allowed; }
.btn-wechat:hover:not(:disabled) { filter:brightness(0.96); }
.hint { margin:10px 0 0; font-size:12px; color:var(--ink-500); text-align:center; }
.hint.muted { margin-top:16px; }
</style>
