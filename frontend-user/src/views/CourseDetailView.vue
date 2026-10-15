<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import http from '@/api/http'
import { formatDuration, mediaUrl, toast } from '@/utils/toast'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const data = ref(null)
const loading = ref(true)

const firstLesson = computed(() => data.value?.lessons?.[0])
const continueLesson = computed(() => {
  const d = data.value
  if (!d?.lessons?.length) return null
  const id = d.continueLessonId
  if (id) return d.lessons.find(l => String(l.id) === String(id)) || firstLesson.value
  for (const l of d.lessons) {
    const p = lessonProg(l)
    if (!p?.completed) return l
  }
  return d.lessons[d.lessons.length - 1]
})
const startLabel = computed(() => {
  const d = data.value
  if (!d) return '开始学习'
  if ((d.completedLessons || 0) >= (d.lessons?.length || 0) && (d.lessons?.length || 0) > 0) return '再看一遍'
  if (d.completedLessons || d.progressMap && Object.keys(d.progressMap).length) return '继续学习'
  return '开始学习'
})

function lessonProg(l) {
  const map = data.value?.progressMap || {}
  return map[String(l.id)] || map[l.id] || null
}

onMounted(async () => {
  try {
    const res = await http.get(`/public/courses/${route.params.id}`)
    data.value = res.data
  } catch (e) { toast(e.message, 'error') }
  finally { loading.value = false }
})
</script>

<template>
  <div class="page container" v-if="!loading && data">
    <div class="hero card">
      <div class="cover" :style="{ backgroundImage: `linear-gradient(135deg,rgba(15,118,110,.9),rgba(15,23,42,.8)), url(${mediaUrl(data.course.coverUrl)})` }" />
      <div class="info">
        <span class="badge">课程详情</span>
        <h1>{{ data.course.title }}</h1>
        <p>{{ data.course.summary }}</p>
        <div class="stats">
          <span>{{ data.course.lessonCount }} 课时</span>
          <span>{{ formatDuration(data.course.durationSeconds) }}</span>
          <span>完成进度 {{ Math.round(data.courseProgress || 0) }}%</span>
        </div>
        <RouterLink v-if="auth.isLogin && continueLesson" class="btn btn-primary"
          :to="`/play/${data.course.id}/${continueLesson.id}`">{{ startLabel }}</RouterLink>
        <RouterLink v-else-if="!auth.isLogin" class="btn btn-primary" to="/login">登录后学习</RouterLink>
        <template v-if="auth.isLogin">
          <RouterLink class="btn btn-soft" :to="`/ai?tab=points&courseId=${data.course.id}`">知识点</RouterLink>
          <RouterLink class="btn btn-soft" :to="`/ai?tab=ask&courseId=${data.course.id}`">去提问</RouterLink>
          <RouterLink class="btn btn-soft" :to="`/ai?tab=quiz&courseId=${data.course.id}`">智能测验</RouterLink>
        </template>
      </div>
    </div>

    <div class="layout">
      <div class="card block">
        <h2>课程介绍</h2>
        <div class="rich" v-html="data.course.description || '暂无详细介绍'" />
        <div v-if="data.course.outline" style="margin-top:18px">
          <h2>章节提纲</h2>
          <pre class="outline">{{ data.course.outline }}</pre>
        </div>
      </div>
      <div class="card block">
        <h2>课时列表</h2>
        <div class="lessons">
          <RouterLink v-for="(l,i) in data.lessons" :key="l.id"
            :to="auth.isLogin ? `/play/${data.course.id}/${l.id}` : '/login'" class="lesson">
            <span class="idx">{{ i+1 }}</span>
            <div>
              <strong>{{ l.title }}</strong>
              <small>{{ formatDuration(l.durationSeconds) }}</small>
            </div>
            <span class="st" v-if="lessonProg(l)?.completed">已完成</span>
            <span class="st muted" v-else-if="lessonProg(l)">学习中</span>
          </RouterLink>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="page container"><div class="skeleton" style="height:320px" /></div>
</template>

<style scoped>
.hero { display:grid; grid-template-columns: 1.1fr 1.2fr; overflow:hidden; margin-bottom: 18px; }
.cover { min-height: 280px; background-size:cover; background-position:center; }
.info { padding: 28px; }
h1 { margin: 10px 0 10px; font-size: 30px; letter-spacing:-.02em; }
.info > p { color: var(--ink-500); line-height:1.6; }
.stats { display:flex; gap:14px; margin: 16px 0 20px; color: var(--ink-500); font-size: 13px; }
.info .btn { margin-right:8px; margin-bottom:8px; }
.layout { display:grid; grid-template-columns: 1.2fr .9fr; gap: 16px; }
.block { padding: 20px; }
.block h2 { margin: 0 0 12px; font-size: 18px; }
.rich { color: var(--ink-700); line-height: 1.7; }
.outline { white-space: pre-wrap; background: var(--ink-50); padding: 12px; border-radius: 12px; font-family: inherit; }
.lessons { display:flex; flex-direction:column; gap:8px; }
.lesson {
  display:flex; align-items:center; gap:12px; padding: 12px; border-radius: 12px; background: var(--ink-50);
}
.lesson:hover { background: var(--brand-50); }
.idx {
  width:28px; height:28px; border-radius:8px; display:grid; place-items:center;
  background:#fff; font-weight:700; color: var(--brand-800); font-size:13px;
}
.lesson strong { display:block; font-size:14px; }
.lesson small { color: var(--ink-500); }
.st { margin-left:auto; font-size:12px; color: var(--brand-700); font-weight:700; }
.st.muted { color: var(--ink-500); }
@media (max-width:960px){ .hero,.layout{grid-template-columns:1fr} }
@media (max-width:640px){
  .info{padding:16px}
  h1{font-size:22px}
  .cover{min-height:180px}
  .block{padding:14px}
  .stats{flex-wrap:wrap; gap:8px 12px}
  .lesson{padding:12px 10px; min-height:48px}
}
</style>
