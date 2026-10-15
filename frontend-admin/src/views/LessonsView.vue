<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const courseId = Number(route.params.id)
const course = ref(null)
const list = ref([])
const dialog = ref(false)
const form = reactive({
  id: null, courseId, title: '', description: '', videoPath: '',
  durationSeconds: 0, sortOrder: 1, freePreview: false
})
const uploading = ref(false)
const uploadPct = ref(0)
const rowUploading = ref({}) // lessonId -> pct
const quick = reactive({ title: '', file: null })
const quickUploading = ref(false)
const quickPct = ref(0)

const courseTitle = computed(() => course.value?.title || `课程 #${courseId}`)
const missingVideo = computed(() => list.value.filter(l => !l.videoPath).length)

async function loadCourse() {
  try {
    const res = await http.get('/admin/courses', { params: { page: 1, size: 200 } })
    course.value = (res.data.list || []).find(c => c.id === courseId) || null
  } catch { /* ignore */ }
}

async function load() {
  list.value = (await http.get(`/admin/courses/${courseId}/lessons`)).data
}

function open(row) {
  Object.assign(form, row || {
    id: null, courseId, title: '', description: '', videoPath: '',
    durationSeconds: 0, sortOrder: (list.value.length || 0) + 1, freePreview: false
  })
  dialog.value = true
}

async function save() {
  if (!form.title?.trim()) { ElMessage.warning('请填写课时标题'); return }
  await http.post('/admin/lessons', { ...form, courseId })
  ElMessage.success('课时已保存')
  dialog.value = false
  await load()
  await loadCourse()
}

async function remove(row) {
  await ElMessageBox.confirm(`删除课时「${row.title}」？`, '确认', { type: 'warning' })
  await http.delete(`/admin/lessons/${row.id}`)
  ElMessage.success('已删除')
  await load()
  await loadCourse()
}

function uploadWithProgress(file, onPct) {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('type', 'video')
  return http.post('/admin/upload', fd, {
    onUploadProgress: (e) => {
      if (e.total) onPct(Math.round((e.loaded / e.total) * 100))
    }
  })
}

async function onUpload(opt) {
  uploading.value = true
  uploadPct.value = 0
  try {
    const res = await uploadWithProgress(opt.file, (p) => { uploadPct.value = p })
    form.videoPath = res.data.path
    ElMessage.success('视频已上传')
    opt.onSuccess?.(res)
  } catch (e) {
    opt.onError?.(e)
  } finally {
    uploading.value = false
    uploadPct.value = 0
  }
}

async function uploadForRow(row, file) {
  if (!file) return
  rowUploading.value = { ...rowUploading.value, [row.id]: 1 }
  try {
    const res = await uploadWithProgress(file, (p) => {
      rowUploading.value = { ...rowUploading.value, [row.id]: p }
    })
    await http.post('/admin/lessons', { ...row, videoPath: res.data.path })
    ElMessage.success(`「${row.title}」视频已上传`)
    await load()
    await loadCourse()
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    const next = { ...rowUploading.value }
    delete next[row.id]
    rowUploading.value = next
  }
}

function onRowFile(row, ev) {
  const file = ev.target.files?.[0]
  ev.target.value = ''
  if (file) uploadForRow(row, file)
}

function onQuickFile(ev) {
  quick.file = ev.target.files?.[0] || null
}

async function quickAdd() {
  if (!quick.title?.trim()) { ElMessage.warning('请填写课时标题'); return }
  if (!quick.file) { ElMessage.warning('请选择视频文件'); return }
  quickUploading.value = true
  quickPct.value = 0
  try {
    const res = await uploadWithProgress(quick.file, (p) => { quickPct.value = p })
    await http.post('/admin/lessons', {
      courseId,
      title: quick.title.trim(),
      description: '',
      videoPath: res.data.path,
      durationSeconds: 0,
      sortOrder: (list.value.length || 0) + 1,
      freePreview: false
    })
    ElMessage.success('课时已添加并挂载视频')
    quick.title = ''
    quick.file = null
    quickPct.value = 0
    const input = document.getElementById('quick-file')
    if (input) input.value = ''
    await load()
    await loadCourse()
  } catch (e) {
    ElMessage.error(e.message || '添加失败')
  } finally {
    quickUploading.value = false
  }
}

function previewUrl(path) {
  if (!path) return ''
  const base = (path.startsWith('http') || path.startsWith('/api/')) ? path : `/api/media/${path}`
  const token = auth.token
  if (!token) return base
  const sep = base.includes('?') ? '&' : '?'
  return `${base}${sep}token=${encodeURIComponent(token)}`
}

onMounted(async () => { await Promise.all([loadCourse(), load()]) })
</script>

<template>
  <div>
    <div class="page-card head">
      <div>
        <el-button link type="primary" @click="router.push('/courses')">← 返回课程列表</el-button>
        <h2 style="margin:6px 0 4px">{{ courseTitle }}</h2>
        <p class="hint">一门课程可包含多节课时，每节独立上传视频。未上传视频的课时学员无法播放。</p>
        <div class="tags">
          <el-tag type="info">共 {{ list.length }} 节</el-tag>
          <el-tag v-if="missingVideo" type="warning">{{ missingVideo }} 节缺视频</el-tag>
          <el-tag v-else-if="list.length" type="success">视频已齐</el-tag>
          <el-tag v-if="course">状态 {{ course.status }}</el-tag>
        </div>
      </div>
      <el-button type="primary" size="large" @click="open()">+ 新增课时</el-button>
    </div>

    <div class="page-card quick">
      <h3>快速添加课时（标题 + 视频）</h3>
      <div class="quick-row">
        <el-input v-model="quick.title" placeholder="课时标题，如：第 1 节 企业文化" clearable style="flex:1" />
        <label class="file-btn">
          <input id="quick-file" type="file" accept="video/*" hidden @change="onQuickFile" />
          <el-button>{{ quick.file ? quick.file.name : '选择视频文件' }}</el-button>
        </label>
        <el-button type="success" :loading="quickUploading" @click="quickAdd">上传并添加</el-button>
      </div>
      <el-progress v-if="quickUploading" :percentage="quickPct" style="margin-top:10px" />
    </div>

    <div class="page-card">
      <div v-if="!list.length" class="empty">
        <p>还没有课时。用上方「快速添加」上传第一节视频，或点「新增课时」。</p>
      </div>
      <div v-for="row in list" :key="row.id" class="lesson-card">
        <div class="meta">
          <div class="ord">#{{ row.sortOrder }}</div>
          <div class="info">
            <strong>{{ row.title }}</strong>
            <div class="sub">
              <span>时长 {{ row.durationSeconds || 0 }} 秒</span>
              <el-tag v-if="row.freePreview" size="small" type="success">试看</el-tag>
              <el-tag v-if="row.videoPath" size="small" type="success">已上传</el-tag>
              <el-tag v-else size="small" type="danger">缺视频</el-tag>
            </div>
            <div v-if="row.videoPath" class="path">{{ row.videoPath }}</div>
          </div>
        </div>
        <div class="ops">
          <label class="upload-big">
            <input type="file" accept="video/*" hidden @change="onRowFile(row, $event)" />
            <el-button type="primary" :loading="!!rowUploading[row.id]">
              {{ row.videoPath ? '更换视频' : '上传本节视频' }}
            </el-button>
          </label>
          <el-progress v-if="rowUploading[row.id]" :percentage="rowUploading[row.id]" style="width:120px" />
          <el-button @click="open(row)">编辑</el-button>
          <el-button type="danger" plain @click="remove(row)">删除</el-button>
        </div>
        <div v-if="row.videoPath" class="preview">
          <video :src="previewUrl(row.videoPath)" controls preload="metadata" />
        </div>
      </div>
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑课时' : '新增课时'" width="680px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:14px"
        title="视频在本页上传：一门课可有多节，每节各自上传一个视频文件。" />
      <el-form label-width="100px">
        <el-form-item label="标题" required><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="时长(秒)">
          <el-input-number v-model="form.durationSeconds" :min="0" />
          <span class="hint" style="margin-left:8px">可填 0，学员播放时会按真实视频时长自动校正</span>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="1" /></el-form-item>
        <el-form-item label="试看"><el-switch v-model="form.freePreview" /></el-form-item>
        <el-form-item label="视频路径"><el-input v-model="form.videoPath" placeholder="上传后自动填入" /></el-form-item>
        <el-form-item label="上传视频">
          <el-upload :http-request="onUpload" :show-file-list="false" accept="video/*" drag>
            <div class="drag-inner">
              <p><strong>拖拽视频到此处</strong>，或点击选择文件</p>
              <p class="hint">支持 mp4 / webm / mov，单文件最大约 500MB</p>
              <el-button :loading="uploading" type="primary">选择本地视频上传</el-button>
            </div>
          </el-upload>
          <el-progress v-if="uploading" :percentage="uploadPct" style="margin-top:8px;width:100%" />
          <video v-if="form.videoPath" :src="previewUrl(form.videoPath)" controls
            style="width:100%;margin-top:10px;max-height:220px;background:#000;border-radius:8px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog=false">取消</el-button>
        <el-button type="primary" @click="save">保存课时</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; margin-bottom:12px; }
.hint { color:#64748b; font-size:13px; margin:0; }
.tags { display:flex; gap:8px; flex-wrap:wrap; margin-top:10px; }
.quick h3 { margin:0 0 12px; font-size:15px; }
.quick-row { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
.file-btn { margin:0; }
.empty { padding:28px; text-align:center; color:#64748b; }
.lesson-card {
  border:1px solid #e2e8f0; border-radius:12px; padding:16px; margin-bottom:12px;
  display:grid; grid-template-columns: 1fr auto; gap:12px 16px; align-items:center;
}
.meta { display:flex; gap:12px; align-items:flex-start; }
.ord {
  width:42px; height:42px; border-radius:10px; background:#ecfdf5; color:#0f766e;
  display:grid; place-items:center; font-weight:700;
}
.info strong { font-size:16px; }
.sub { display:flex; gap:8px; align-items:center; margin-top:6px; color:#64748b; font-size:13px; }
.path { margin-top:4px; font-size:12px; color:#94a3b8; word-break:break-all; }
.ops { display:flex; gap:8px; align-items:center; flex-wrap:wrap; justify-content:flex-end; }
.upload-big { margin:0; }
.preview { grid-column: 1 / -1; }
.preview video { width:100%; max-height:240px; background:#000; border-radius:8px; }
.drag-inner { padding:18px; text-align:center; }
.drag-inner p { margin:0 0 8px; }
@media (max-width: 900px) {
  .lesson-card { grid-template-columns: 1fr; }
  .ops { justify-content:flex-start; }
}
</style>
