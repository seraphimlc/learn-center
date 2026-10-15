<script setup>
import { onMounted, ref } from 'vue'
import http from '@/api/http'
const list = ref([])
onMounted(async () => { list.value = (await http.get('/admin/certificates')).data })
</script>
<template>
  <div class="page-card">
    <el-table :data="list" stripe>
      <el-table-column prop="certNo" label="证书编号" min-width="200" />
      <el-table-column prop="userName" label="学员" width="140" />
      <el-table-column prop="courseTitle" label="课程" />
      <el-table-column prop="completionRate" label="完成率" width="100">
        <template #default="{row}">{{ Math.round((row.completionRate||0)*100) }}%</template>
      </el-table-column>
      <el-table-column prop="issuedAt" label="颁发时间" width="180" />
    </el-table>
  </div>
</template>
