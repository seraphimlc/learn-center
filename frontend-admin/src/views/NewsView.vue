<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const list = ref([])
const total = ref(0)
const page = ref(1)
const dialog = ref(false)
const form = reactive({ id: null, title: '', summary: '', content: '', status: 'DRAFT', coverUrl: '' })

async function load() {
  const res = await http.get('/admin/news', { params: { page: page.value, size: 20 } })
  list.value = res.data.list; total.value = res.data.total
}
function open(row) {
  Object.assign(form, row || { id: null, title: '', summary: '', content: '', status: 'DRAFT', coverUrl: '' })
  dialog.value = true
}
async function save() {
  await http.post('/admin/news', form)
  ElMessage.success('已保存'); dialog.value = false; load()
}
async function remove(row) {
  await http.delete(`/admin/news/${row.id}`); ElMessage.success('已删除'); load()
}
onMounted(load)
</script>
<template>
  <div class="page-card">
    <div style="margin-bottom:12px"><el-button type="primary" @click="open()">新建新闻</el-button></div>
    <el-table :data="list" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="publishedAt" label="发布时间" width="180" />
      <el-table-column label="操作" width="180">
        <template #default="{row}">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" title="新闻编辑" width="720px">
      <el-form label-width="80px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="form.summary" type="textarea" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="8" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status"><el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
