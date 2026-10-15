<script setup>
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import http from '@/api/http'
import { toast } from '@/utils/toast'

const router = useRouter()
const form = ref({ username: '', email: '', newPassword: '' })
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await http.post('/auth/reset-password', form.value)
    toast('密码已重置，请登录')
    router.push('/login')
  } catch (e) {
    toast(e.message, 'error')
  } finally { loading.value = false }
}
</script>
<template>
  <div class="page">
    <div class="auth card">
      <div class="head">
        <h1>找回密码</h1>
        <p>验证用户名与注册邮箱后设置新密码</p>
      </div>
      <form @submit.prevent="submit" class="form">
        <label class="label">用户名</label>
        <input class="input" v-model="form.username" required />
        <label class="label" style="margin-top:12px">注册邮箱</label>
        <input class="input" type="email" v-model="form.email" required />
        <label class="label" style="margin-top:12px">新密码</label>
        <input class="input" type="password" v-model="form.newPassword" required minlength="6" placeholder="至少 6 位" />
        <button class="btn btn-primary" style="width:100%;margin-top:18px" :disabled="loading">
          {{ loading ? '提交中…' : '重置密码' }}
        </button>
      </form>
      <div class="foot"><RouterLink to="/login">返回登录</RouterLink></div>
    </div>
  </div>
</template>
<style scoped>
.auth { width: min(420px, 100% - 32px); margin: 40px auto; padding: 32px; }
.head h1 { margin:0 0 6px; }
.head p { margin:0 0 22px; color: var(--ink-500); }
.foot { margin-top: 18px; font-size:13px; }
.foot a { color: var(--brand-700); font-weight:600; }
</style>
