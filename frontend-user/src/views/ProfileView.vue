<script setup>
import { onMounted, ref } from 'vue'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/utils/toast'

const auth = useAuthStore()
const profile = ref({ nickname: '', email: '', phone: '', bio: '' })
const pwd = ref({ oldPassword: '', newPassword: '' })

onMounted(async () => {
  const res = await http.get('/auth/me')
  profile.value = { nickname: res.data.nickname, email: res.data.email, phone: res.data.phone, bio: res.data.bio }
})

async function saveProfile() {
  try {
    await http.put('/auth/profile', profile.value)
    await auth.fetchMe()
    toast('资料已更新')
  } catch (e) { toast(e.message, 'error') }
}
async function changePwd() {
  try {
    await http.post('/auth/change-password', pwd.value)
    pwd.value = { oldPassword: '', newPassword: '' }
    toast('密码已修改')
  } catch (e) { toast(e.message, 'error') }
}
</script>
<template>
  <div class="page container">
    <div class="section-title"><div><h2>个人中心</h2><p>管理资料与登录密码</p></div></div>
    <div class="layout">
      <div class="card box">
        <h3>基本资料</h3>
        <label class="label">昵称</label><input class="input" v-model="profile.nickname" />
        <label class="label" style="margin-top:12px">邮箱</label><input class="input" v-model="profile.email" />
        <label class="label" style="margin-top:12px">手机</label><input class="input" v-model="profile.phone" />
        <label class="label" style="margin-top:12px">简介</label><textarea class="input" rows="3" v-model="profile.bio" />
        <button class="btn btn-primary" style="margin-top:16px" @click="saveProfile">保存资料</button>
      </div>
      <div class="card box">
        <h3>修改密码</h3>
        <label class="label">原密码</label><input class="input" type="password" v-model="pwd.oldPassword" />
        <label class="label" style="margin-top:12px">新密码</label><input class="input" type="password" v-model="pwd.newPassword" />
        <button class="btn btn-soft" style="margin-top:16px" @click="changePwd">更新密码</button>
        <p class="hint">忘记密码可在登录页使用邮箱重置，或联系管理员重置。</p>
      </div>
    </div>
  </div>
</template>
<style scoped>
.layout { display:grid; grid-template-columns:1fr 1fr; gap:16px; }
.box { padding:22px; }
h3 { margin:0 0 14px; }
.hint { margin-top:14px; color:var(--ink-500); font-size:13px; }
@media (max-width:900px){ .layout{grid-template-columns:1fr} }
</style>
