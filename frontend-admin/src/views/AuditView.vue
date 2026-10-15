<script setup>
import { onMounted, ref } from 'vue'
import http from '@/api/http'
const list = ref([]); const total=ref(0); const page=ref(1)
async function load(){ const res = await http.get('/admin/audit-logs', { params:{ page:page.value, size:20 } }); list.value=res.data.list; total.value=res.data.total }
onMounted(load)
</script>
<template>
  <div class="page-card">
    <el-table :data="list" stripe>
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column prop="operatorName" label="操作者" width="120" />
      <el-table-column prop="action" label="动作" width="140" />
      <el-table-column prop="resourceType" label="资源" width="120" />
      <el-table-column prop="detail" label="详情" />
      <el-table-column prop="ip" label="IP" width="140" />
    </el-table>
    <el-pagination style="margin-top:12px" background layout="prev, pager, next" :total="total" v-model:current-page="page" @current-change="load" />
  </div>
</template>
