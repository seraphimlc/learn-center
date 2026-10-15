<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
const title = ref('')
const content = ref('')
async function send(){
  await http.post('/admin/announce', { title: title.value, content: content.value })
  ElMessage.success('公告已推送至站内信')
  title.value=''; content.value=''
}
</script>
<template>
  <div class="page-card" style="max-width:720px">
    <h3 style="margin-top:0">发布站内公告</h3>
    <el-form label-position="top">
      <el-form-item label="标题"><el-input v-model="title" /></el-form-item>
      <el-form-item label="内容"><el-input v-model="content" type="textarea" :rows="6" /></el-form-item>
      <el-button type="primary" @click="send">发送公告</el-button>
    </el-form>
  </div>
</template>
