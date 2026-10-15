<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

const list = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const roles = [
  { value: 'USER', label: '学员' },
  { value: 'ADMIN', label: '超级管理员' },
  { value: 'CONTENT_ADMIN', label: '内容管理员' },
  { value: 'DATA_VIEWER', label: '数据查看员' },
]

async function load() {
  const res = await http.get('/admin/users', { params: { page: page.value, size: 20, keyword: keyword.value || undefined } })
  list.value = res.data.list
  total.value = res.data.total
}
async function toggle(row) {
  await http.post(`/admin/users/${row.id}/enable`, null, { params: { enabled: !row.enabled } })
  ElMessage.success('已更新')
  load()
}
async function resetPwd(row) {
  await ElMessageBox.confirm(`确认重置用户 ${row.username} 的密码为 Reset@123？`, '重置密码')
  await http.post(`/admin/users/${row.id}/reset-password`, { password: 'Reset@123' })
  ElMessage.success('已重置')
}
async function changeRole(row, role) {
  await http.post(`/admin/users/${row.id}/role`, { role })
  ElMessage.success('角色已更新')
  load()
}
onMounted(load)
</script>

<template>
  <div class="page-card">
    <div style="display:flex;gap:10px;margin-bottom:14px">
      <el-input v-model="keyword" placeholder="搜索用户名/昵称/邮箱" style="width:260px" clearable @keyup.enter="page=1;load()" />
      <el-button type="primary" @click="page=1;load()">查询</el-button>
    </div>
    <el-table :data="list" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column label="角色" width="180">
        <template #default="{ row }">
          <el-select :model-value="row.role" size="small" style="width:150px" @change="(v)=>changeRole(row,v)">
            <el-option v-for="r in roles" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><el-tag :type="row.enabled?'success':'info'">{{ row.enabled?'启用':'禁用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="toggle(row)">{{ row.enabled?'禁用':'启用' }}</el-button>
          <el-button link type="warning" @click="resetPwd(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="margin-top:14px;display:flex;justify-content:flex-end">
      <el-pagination background layout="prev, pager, next" :total="total" v-model:current-page="page" @current-change="load" />
    </div>
  </div>
</template>
