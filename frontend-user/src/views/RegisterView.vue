<script setup>
import { onMounted, ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import http from '@/api/http'
import { toast } from '@/utils/toast'

const auth = useAuthStore()
const router = useRouter()
const form = ref({ username: '', password: '', email: '', nickname: '' })
const loading = ref(false)
const wechatEnabled = ref(false)
const wechatLoading = ref(false)

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
    await auth.register(form.value)
    toast('注册成功，已自动登录')
    router.replace('/')
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
      <div class="head"><h1>创建账号</h1><p>加入知行学堂，开启系统化学习</p></div>
      <form @submit.prevent="submit">
        <label class="label">用户名</label>
        <input class="input" v-model="form.username" required minlength="3" />
        <label class="label" style="margin-top:12px">密码</label>
        <input class="input" type="password" v-model="form.password" required minlength="6" />
        <label class="label" style="margin-top:12px">邮箱（找回密码用）</label>
        <input class="input" type="email" v-model="form.email" />
        <label class="label" style="margin-top:12px">昵称</label>
        <input class="input" v-model="form.nickname" />
        <button class="btn btn-primary" style="width:100%;margin-top:18px" :disabled="loading">
          {{ loading ? '提交中…' : '注册' }}
        </button>
      </form>
      <template v-if="wechatEnabled">
        <div class="divider"><span>或</span></div>
        <button class="btn btn-wechat" type="button" :disabled="wechatLoading" @click="wechatLogin">
          {{ wechatLoading ? '跳转中…' : '微信一键登录/注册' }}
        </button>
      </template>
      <p v-else class="hint">已支持微信扫码登录（需配置开放平台 AppId 后显示入口）。</p>
      <div class="foot"><RouterLink to="/login">已有账号？去登录</RouterLink></div>
    </div>
  </div>
</template>

<style scoped>
.auth { width: min(420px, 100% - 32px); margin: 40px auto; padding: 32px; }
.head h1 { margin:0 0 6px; }
.head p { margin:0 0 22px; color: var(--ink-500); }
.foot { margin-top: 18px; font-size:13px; }
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
.hint { margin:14px 0 0; font-size:12px; color:var(--ink-500); text-align:center; }
</style>
