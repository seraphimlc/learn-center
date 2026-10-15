<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
const list = ref([])
const dialog = ref(false)
const form = reactive({ id: null, name: '', parentId: null, sortOrder: 0, enabled: true })
async function load(){ list.value = (await http.get('/admin/categories')).data }
function open(row){ Object.assign(form, row || { id:null, name:'', parentId:null, sortOrder:0, enabled:true }); dialog.value=true }
async function save(){ await http.post('/admin/categories', form); ElMessage.success('已保存'); dialog.value=false; load() }
async function remove(row){ await http.delete(`/admin/categories/${row.id}`); load() }
onMounted(load)
</script>
<template>
  <div class="page-card">
    <el-button type="primary" style="margin-bottom:12px" @click="open()">新增分类</el-button>
    <el-table :data="list" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="sortOrder" label="排序" width="90" />
      <el-table-column label="启用" width="90"><template #default="{row}">{{ row.enabled?'是':'否' }}</template></el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{row}"><el-button link type="primary" @click="open(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dialog" title="分类" width="480px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
