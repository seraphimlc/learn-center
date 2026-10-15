<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import http from '@/api/http'
import { formatDuration, toast } from '@/utils/toast'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const detail = ref(null)
const videoRef = ref(null)
const noteText = ref('')
const notes = ref([])
const bookmarks = ref([])
const lastProgress = ref(null)
let timer = null
let lastPos = 0
let videoDuration = 0
let reporting = false

const lesson = computed(() => detail.value?.lessons?.find(l => String(l.id) === String(route.params.lessonId)))
/** Short-lived signed play URL from /learn/play-url (JWT mint → expires+sig). Not a permanent OSS link. */
const videoSrc = ref('')
let playUrlExpiresAt = 0
let playUrlTimer = null

async function refreshPlayUrl() {
  videoSrc.value = ''
  if (!route.params.lessonId || !auth.token) return
  try {
    const res = await http.get(`/learn/play-url/${route.params.lessonId}`)
    videoSrc.value = res.data.playUrl || ''
    playUrlExpiresAt = Number(res.data.expiresAt || 0)
    // refresh ~60s before expiry so long sessions keep playing
    if (playUrlTimer) clearTimeout(playUrlTimer)
    if (playUrlExpiresAt > 0) {
      const ms = Math.max(30_000, (playUrlExpiresAt * 1000) - Date.now() - 60_000)
      playUrlTimer = setTimeout(() => { refreshPlayUrl() }, ms)
    }
  } catch (e) {
    toast(e.message || '无法获取播放地址', 'error')
  }
}

function progOf(lessonId) {
  const map = detail.value?.progressMap || {}
  return map[String(lessonId)] || map[lessonId] || null
}

async function load() {
  const res = await http.get(`/public/courses/${route.params.courseId}`)
  detail.value = res.data
  lastProgress.value = progOf(route.params.lessonId)
  await refreshPlayUrl()
  const n = await http.get('/learn/notes', { params: { lessonId: route.params.lessonId } })
  notes.value = n.data
  const b = await http.get('/learn/bookmarks', { params: { lessonId: route.params.lessonId } })
  bookmarks.value = b.data
}

async function report(pos, delta, { heartbeat = true, ended = false } = {}) {
  if (reporting) return
  reporting = true
  try {
    const body = {
      lessonId: Number(route.params.lessonId),
      positionSeconds: Math.floor(Math.max(0, pos || 0)),
      deltaSeconds: Math.max(0, Math.floor(delta || 0)),
      heartbeat,
      ended: !!ended
    }
    if (videoDuration > 0) body.videoDurationSeconds = Math.floor(videoDuration)
    const res = await http.post('/learn/progress', body)
    lastProgress.value = res.data
    if (detail.value?.progressMap) {
      detail.value.progressMap[String(route.params.lessonId)] = res.data
    }
    if (res.data?.completed && detail.value) {
      // refresh course aggregate so sidebar ticks update
      const done = Object.values(detail.value.progressMap || {}).filter(p => p.completed).length
      detail.value.completedLessons = done
      const total = detail.value.lessons?.length || 1
      detail.value.courseProgress = Math.round((done * 1000) / total) / 10
    }
  } catch (e) { /* keep UI alive */ }
  finally { reporting = false }
}

function onTimeUpdate() {
  // heartbeat interval handles periodic report
}

function onLoaded() {
  const v = videoRef.value
  if (!v) return
  if (Number.isFinite(v.duration) && v.duration > 0) {
    videoDuration = v.duration
  }
  const p = progOf(route.params.lessonId)
  const resume = p?.positionSeconds || 0
  const dur = videoDuration || lesson.value?.durationSeconds || 0
  if (resume > 0 && (!p?.completed) && (!dur || resume < dur - 2)) {
    v.currentTime = resume
  }
  lastPos = v.currentTime
  // sync duration early so completion threshold is correct
  report(v.currentTime, 0, { heartbeat: true })
}

async function onEnded() {
  const v = videoRef.value
  const pos = v ? v.currentTime : (videoDuration || 0)
  const delta = Math.max(0, pos - lastPos)
  lastPos = pos
  await report(pos, delta, { heartbeat: true, ended: true })
  toast('本节已学完')
}

function onPause() {
  const v = videoRef.value
  if (!v) return
  const now = v.currentTime
  const delta = Math.max(0, now - lastPos)
  lastPos = now
  report(now, delta, { heartbeat: true })
}

async function addNote() {
  if (!noteText.value.trim()) return
  const pos = Math.floor(videoRef.value?.currentTime || 0)
  try {
    await http.post('/learn/notes', { lessonId: Number(route.params.lessonId), positionSeconds: pos, content: noteText.value })
    noteText.value = ''
    toast('笔记已保存')
    const n = await http.get('/learn/notes', { params: { lessonId: route.params.lessonId } })
    notes.value = n.data
  } catch (e) { toast(e.message, 'error') }
}

async function addBookmark() {
  const pos = Math.floor(videoRef.value?.currentTime || 0)
  try {
    await http.post('/learn/bookmarks', { lessonId: Number(route.params.lessonId), positionSeconds: pos, title: `书签 ${formatDuration(pos)}` })
    toast('书签已添加')
    const b = await http.get('/learn/bookmarks', { params: { lessonId: route.params.lessonId } })
    bookmarks.value = b.data
  } catch (e) { toast(e.message, 'error') }
}

function seek(sec) {
  if (videoRef.value) videoRef.value.currentTime = sec
}

function lessonDone(l) {
  return !!progOf(l.id)?.completed
}
function lessonStarted(l) {
  return !!progOf(l.id)
}

onMounted(async () => {
  await load()
  timer = setInterval(() => {
    const v = videoRef.value
    if (!v || v.paused) return
    const now = v.currentTime
    const delta = Math.max(0, now - lastPos)
    lastPos = now
    report(now, delta, { heartbeat: true })
  }, 3000) // ~3s progress heartbeat
})

watch(() => route.params.lessonId, async () => {
  await load()
  lastPos = 0
  videoDuration = 0
})

onBeforeUnmount(() => {
  clearInterval(timer)
  if (playUrlTimer) clearTimeout(playUrlTimer)
  const v = videoRef.value
  if (v) report(v.currentTime, 0, { heartbeat: true })
})
</script>

<template>
  <div class="page container" v-if="detail && lesson">
    <div class="layout">
      <div>
        <div class="player card">
          <!-- Anti-download (baseline only, not DRM): nodownload + block context menu; bytes still flow to the browser via signed URL. -->
          <video ref="videoRef" :src="videoSrc" controls controlsList="nodownload noplaybackrate"
            disablePictureInPicture
            @contextmenu.prevent
            @timeupdate="onTimeUpdate" @loadedmetadata="onLoaded"
            @ended="onEnded" @pause="onPause" />
        </div>
        <div class="card meta">
          <div class="badges">
            <span class="badge" v-if="lastProgress?.completed || lessonDone(lesson)">已完成</span>
            <span class="badge muted" v-else-if="lastProgress">学习中 · {{ formatDuration(lastProgress.positionSeconds) }}</span>
          </div>
          <h1>{{ lesson.title }}</h1>
          <p>{{ detail.course.title }} · 课程进度 {{ Math.round(detail.courseProgress || 0) }}%（{{ detail.completedLessons || 0 }}/{{ detail.lessons?.length || 0 }}）</p>
          <div class="actions">
            <button class="btn btn-soft" @click="addBookmark">加书签</button>
            <RouterLink class="btn btn-ghost" :to="`/courses/${detail.course.id}`">返回课程</RouterLink>
            <RouterLink class="btn btn-soft"
              :to="`/ai?tab=ask&courseId=${detail.course.id}&lessonId=${lesson.id}`">AI 提问</RouterLink>
            <RouterLink class="btn btn-soft"
              :to="`/ai?tab=digest&lessonId=${lesson.id}&courseId=${detail.course.id}`">本课摘要</RouterLink>
            <RouterLink class="btn btn-soft"
              :to="`/ai?tab=points&courseId=${detail.course.id}&lessonId=${lesson.id}`">知识点</RouterLink>
          </div>
        </div>
        <div class="card note-box">
          <h3>学习笔记</h3>
          <textarea class="input" rows="3" v-model="noteText" placeholder="记录当前时间点的想法…" />
          <button class="btn btn-primary" style="margin-top:10px" @click="addNote">保存笔记</button>
          <div class="list">
            <div v-for="n in notes" :key="n.id" class="item" @click="seek(n.positionSeconds)">
              <strong>{{ formatDuration(n.positionSeconds) }}</strong>
              <span>{{ n.content }}</span>
            </div>
            <div v-if="!notes.length" class="empty-mini">暂无笔记</div>
          </div>
        </div>
      </div>
      <aside class="card side">
        <h3>课时目录</h3>
        <RouterLink v-for="(l,i) in detail.lessons" :key="l.id"
          :to="`/play/${detail.course.id}/${l.id}`"
          :class="['row', {on: String(l.id)===String(route.params.lessonId)}]">
          <span>{{ i+1 }}. {{ l.title }}</span>
          <small v-if="lessonDone(l)" class="ok">完成</small>
          <small v-else-if="lessonStarted(l)">学习中</small>
          <small v-else>{{ formatDuration(l.durationSeconds) }}</small>
        </RouterLink>
        <h3 style="margin-top:18px">书签</h3>
        <button v-for="b in bookmarks" :key="b.id" class="bm" @click="seek(b.positionSeconds)">
          {{ b.title || formatDuration(b.positionSeconds) }}
        </button>
        <div v-if="!bookmarks.length" class="empty-mini">暂无书签</div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.layout { display:grid; grid-template-columns: 1.7fr .75fr; gap: 16px; }
.player { overflow:hidden; background:#000; }
video { width:100%; max-width:100%; height:auto; max-height: 62vh; background:#000; display:block; }
.row, .bm, .item, .actions .btn { touch-action: manipulation; }
.row { min-height: 44px; align-items:center; }
.bm { min-height: 40px; }
.meta { padding: 18px 20px; margin-top: 12px; }
.meta h1 { margin:0 0 6px; font-size:22px; }
.meta p { margin:0; color:var(--ink-500); }
.badges { display:flex; gap:8px; margin-bottom:8px; }
.badge { background:var(--brand-50); color:var(--brand-800); font-size:12px; font-weight:700; padding:4px 8px; border-radius:999px; }
.badge.muted { background:var(--ink-50); color:var(--ink-600); }
.actions { margin-top: 12px; display:flex; gap:8px; flex-wrap:wrap; }
.note-box, .side { padding: 16px; margin-top: 12px; }
.side { margin-top:0; height: fit-content; position: sticky; top: 84px; }
.side h3, .note-box h3 { margin: 0 0 12px; font-size: 15px; }
.row {
  display:flex; justify-content:space-between; gap:8px; padding:10px; border-radius:10px; font-size:13px; color:var(--ink-700);
}
.row.on, .row:hover { background: var(--brand-50); color: var(--brand-800); }
.ok { color: var(--brand-700); font-weight:700; }
.list { margin-top: 14px; display:flex; flex-direction:column; gap:8px; }
.item { padding:10px; border-radius:10px; background:var(--ink-50); cursor:pointer; display:flex; flex-direction:column; gap:4px; }
.item strong { color: var(--brand-700); font-size:12px; }
.bm { display:block; width:100%; text-align:left; margin-bottom:6px; border:none; background:var(--ink-50); border-radius:8px; padding:8px 10px; cursor:pointer; }
.empty-mini { color:var(--ink-500); font-size:13px; padding:8px 0; }
@media (max-width:960px){ .layout{grid-template-columns:1fr} .side{position:static} }
@media (max-width:640px){
  .meta{padding:14px}
  .meta h1{font-size:18px}
  .note-box,.side{padding:14px}
  .actions .btn{flex:1 1 auto; min-width:calc(50% - 8px); justify-content:center}
}
</style>
