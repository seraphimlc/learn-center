<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

const router = useRouter()
const list = ref([])
const total = ref(0)
const page = ref(1)
const dialog = ref(false)
const form = reactive({
  id: null, title: '', categoryId: null, summary: '', description: '',
  status: 'DRAFT', hot: false, recommended: false, completionRatio: 0.9, coverUrl: ''
})
const cats = ref([])
const creating = ref(false)

async function load() {
  const res = await http.get('/admin/courses', { params: { page: page.value, size: 20 } })
  list.value = res.data.list
  total.value = res.data.total
}

function open(row) {
  Object.assign(form, row || {
    id: null, title: '', categoryId: null, summary: '', description: '',
    status: 'DRAFT', hot: false, recommended: false, completionRatio: 0.9, coverUrl: ''
  })
  creating.value = !row?.id
  dialog.value = true
}

async function save() {
  if (!form.title?.trim()) { ElMessage.warning('请填写课程标题'); return }
  const wasNew = !form.id
  const res = await http.post('/admin/courses', form)
  ElMessage.success('课程已保存')
  dialog.value = false
  await load()
  if (wasNew && res.data?.id) {
    ElMessage.info('请继续上传各节课时视频')
    router.push(`/courses/${res.data.id}/lessons`)
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`删除课程「${row.title}」及其全部课时？`, '确认', { type: 'warning' })
  await http.delete(`/admin/courses/${row.id}`)
  load()
}

async function ai(row, apply = false) {
  const res = await http.post(`/admin/ai/course/${row.id}`, null, { params: { apply } })
  ElMessage.success(apply ? 'AI 内容已写入课程' : 'AI 已生成（未写入）')
  if (!apply) ElMessage.info((res.data.summary || '').slice(0, 80))
  if (apply) load()
}

function goLessons(row) {
  router.push(`/courses/${row.id}/lessons`)
}

onMounted(async () => {
  cats.value = (await http.get('/admin/categories')).data
  load()
})
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <div>
        <h3 style="margin:0 0 4px">课程管理</h3>
        <p class="hint">一门课程可包含多节课时；视频在「管理课时/视频」中按节上传。</p>
      </div>
      <el-button type="primary" @click="open()">新建课程</el-button>
    </div>
    <el-table :data="list" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column label="课时/视频" width="120">
        <template #default="{ row }">
          <el-tag :type="row.lessonCount ? 'success' : 'warning'">{{ row.lessonCount || 0 }} 节</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标记" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.hot" size="small">热门</el-tag>
          <el-tag v-if="row.recommended" type="success" size="small">推荐</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="420">
        <template #default="{ row }">
          <el-button type="primary" @click="goLessons(row)">管理课时/视频</el-button>
          <el-button link type="primary" @click="open(row)">编辑信息</el-button>
          <el-button link type="success" @click="ai(row, false)">AI摘要</el-button>
          <el-button link type="warning" @click="ai(row, true)">AI写入</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="total > 20"
      style="margin-top:12px;justify-content:flex-end"
      layout="prev, pager, next"
      :total="total"
      v-model:current-page="page"
      @current-change="load"
    />

    <el-dialog v-model="dialog" :title="form.id ? '编辑课程信息' : '新建课程'" width="720px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom:14px"
        title="视频请在课时里上传：一门课可多节，每节各自上传视频。保存新建课程后会自动进入课时管理页。"
      />
      <el-form label-width="100px">
        <el-form-item label="标题" required><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" clearable style="width:100%">
            <el-option v-for="c in cats" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要"><el-input v-model="form.summary" type="textarea" /></el-form-item>
        <el-form-item label="介绍"><el-input v-model="form.description" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="封面URL"><el-input v-model="form.coverUrl" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="下架" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="结业比例"><el-input-number v-model="form.completionRatio" :min="0.5" :max="1" :step="0.05" /></el-form-item>
        <el-form-item label="热门"><el-switch v-model="form.hot" /></el-form-item>
        <el-form-item label="推荐"><el-switch v-model="form.recommended" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog=false">取消</el-button>
        <el-button type="primary" @click="save">{{ form.id ? '保存' : '保存并管理课时' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { display:flex; justify-content:space-between; align-items:flex-start; gap:12px; margin-bottom:14px; }
.hint { margin:0; color:#64748b; font-size:13px; }
</style>
