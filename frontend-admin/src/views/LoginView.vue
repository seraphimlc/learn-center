<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const form = ref({ username: 'admin', password: 'Admin@123' })
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await auth.login(form.value)
    ElMessage.success('登录成功')
    router.replace('/')
  } catch (e) {
    ElMessage.error(e.message)
  } finally { loading.value = false }
}
</script>

<template>
  <div class="login-wrap">
    <div class="login-card">
      <h1>管理后台登录</h1>
      <p>知行学堂运营控制台 · 仅管理员可进入</p>
      <el-form @submit.prevent="submit" label-position="top">
        <el-form-item label="管理员账号"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">进入控制台</el-button>
      </el-form>
      <div style="margin-top:14px;color:#94a3b8;font-size:12px">预置账号已填入，生产环境请立即修改密码</div>
    </div>
  </div>
</template>
