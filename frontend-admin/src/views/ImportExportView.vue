<script setup>
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

async function downloadBlob(url, filename) {
  const res = await http.get(url, { responseType: 'blob' })
  const blob = new Blob([res.data])
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = filename; a.click()
}
async function exportStudy(){ await downloadBlob('/admin/export/study', 'study-export.csv'); ElMessage.success('已导出') }
async function template(){ await downloadBlob('/admin/users/import-template', 'user-import-template.csv') }
async function onImport(opt){
  try {
    const fd = new FormData(); fd.append('file', opt.file)
    const res = await http.post('/admin/users/import', fd)
    ElMessage.success(`导入成功 ${res.data.imported}，跳过 ${res.data.skipped}`)
    opt.onSuccess(res)
  } catch(e){ opt.onError(e); ElMessage.error(e.message || '导入失败') }
}
</script>
<template>
  <div class="page-card">
    <h3 style="margin-top:0">批量导入导出</h3>
    <el-space wrap>
      <el-button type="primary" @click="exportStudy">导出学习数据 CSV</el-button>
      <template v-if="auth.canManageUsers">
        <el-button @click="template">下载用户导入模板</el-button>
        <el-upload :http-request="onImport" :show-file-list="false" accept=".csv">
          <el-button type="success">导入用户 CSV</el-button>
        </el-upload>
      </template>
    </el-space>
    <p style="color:#64748b;margin-top:16px">
      学习数据导出对数据查看员开放；用户 CSV 导入仅超级管理员可用。
    </p>
  </div>
</template>
