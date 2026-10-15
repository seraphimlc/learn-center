<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
const list = ref([])
async function load(){ list.value = (await http.get('/admin/configs')).data }
async function save(row){ await http.post('/admin/configs', row); ElMessage.success('已保存') }
onMounted(load)
</script>
<template>
  <div class="page-card">
    <h3 style="margin-top:0">运营 / 学习规则配置</h3>
    <el-table :data="list">
      <el-table-column prop="configKey" label="键" width="240" />
      <el-table-column label="值"><template #default="{row}"><el-input v-model="row.configValue" /></template></el-table-column>
      <el-table-column prop="remark" label="说明" width="220" />
      <el-table-column label="操作" width="100"><template #default="{row}"><el-button link type="primary" @click="save(row)">保存</el-button></template></el-table-column>
    </el-table>
  </div>
</template>
