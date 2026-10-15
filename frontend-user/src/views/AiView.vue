<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import http from '@/api/http'
import CourseCard from '@/components/CourseCard.vue'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const tabs = [
  { id: 'analysis', label: '学情分析' },
  { id: 'ask', label: '课程问答' },
  { id: 'points', label: '知识点' },
  { id: 'quiz', label: '智能测验' },
  { id: 'digest', label: '本课摘要' },
  { id: 'recommend', label: '推荐课程' },
]
const tab = ref(route.query.tab || 'analysis')
const courses = ref([])
const courseId = ref(route.query.courseId ? Number(route.query.courseId) : null)
const lessonId = ref(route.query.lessonId ? Number(route.query.lessonId) : null)
const lessons = ref([])
const loading = ref(false)

const analysis = ref(null)
const askQ = ref('')
const askA = ref(null)
const points = ref(null)
const quiz = ref(null)
const answers = ref({})
const grade = ref(null)
const digest = ref(null)
const recommend = ref([])

const selectedCourse = computed(() => courses.value.find(c => c.id === courseId.value))

watch(tab, (v) => {
  router.replace({ query: { ...route.query, tab: v } })
})

watch(courseId, async (id) => {
  lessons.value = []
  lessonId.value = route.query.lessonId && Number(route.query.courseId) === id
    ? Number(route.query.lessonId) : null
  if (!id) return
  try {
    const res = await http.get(`/public/courses/${id}`)
    lessons.value = res.data.lessons || []
    if (!lessonId.value && lessons.value[0]) lessonId.value = lessons.value[0].id
  } catch {}
})

async function loadCourses() {
  const res = await http.get('/public/courses', { params: { page: 1, size: 50 } })
  courses.value = res.data.list || []
  if (!courseId.value && courses.value[0]) courseId.value = courses.value[0].id
}

async function loadAnalysis() {
  loading.value = true
  try {
    analysis.value = (await http.get('/ai/analysis')).data
  } catch (e) { toast(e.message, 'error') }
  finally { loading.value = false }
}

async function loadRecommend() {
  loading.value = true
  try {
    recommend.value = (await http.get('/ai/recommend')).data || []
  } catch (e) { toast(e.message, 'error') }
  finally { loading.value = false }
}

async function doAsk() {
  if (!courseId.value || !askQ.value.trim()) { toast('请选择课程并输入问题', 'error'); return }
  loading.value = true
  askA.value = null
  try {
    askA.value = (await http.post('/ai/ask', {
      courseId: courseId.value, lessonId: lessonId.value || undefined, question: askQ.value
    })).data
  } catch (e) { toast(e.message, 'error') }
  finally { loading.value = false }
}

async function doPoints() {
  if (!courseId.value) { toast('请选择课程', 'error'); return }
  loading.value = true
  points.value = null
  try {
    points.value = (await http.post('/ai/knowledge-points', {
      courseId: courseId.value, lessonId: lessonId.value || undefined
    })).data
  } catch (e) { toast(e.message, 'error') }
  finally { loading.value = false }
}

async function doQuiz() {
  if (!courseId.value) { toast('请选择课程', 'error'); return }
  loading.value = true
  quiz.value = null
  grade.value = null
  answers.value = {}
  try {
    quiz.value = (await http.post('/ai/quiz', {
      courseId: courseId.value, lessonId: lessonId.value || undefined, count: 5
    })).data
  } catch (e) { toast(e.message, 'error') }
  finally { loading.value = false }
}

async function doGrade() {
  if (!quiz.value?.questions?.length) return
  loading.value = true
  try {
    const payload = quiz.value.questions.map((q, index) => ({
      questionId: q.id,
      index,
      selectedIndex: answers.value[q.id] ?? -1
    }))
    grade.value = (await http.post('/ai/quiz/grade', {
      courseId: courseId.value, answers: payload
    })).data
    toast(`得分 ${grade.value.score} 分`)
  } catch (e) { toast(e.message, 'error') }
  finally { loading.value = false }
}

async function doDigest() {
  if (!lessonId.value) { toast('请选择课时', 'error'); return }
  loading.value = true
  digest.value = null
  try {
    digest.value = (await http.post('/ai/lesson-digest', { lessonId: lessonId.value })).data
  } catch (e) { toast(e.message, 'error') }
  finally { loading.value = false }
}

onMounted(async () => {
  await loadCourses()
  if (courseId.value) {
    try {
      const res = await http.get(`/public/courses/${courseId.value}`)
      lessons.value = res.data.lessons || []
    } catch {}
  }
  if (tab.value === 'analysis') await loadAnalysis()
  if (tab.value === 'recommend') await loadRecommend()
  if (tab.value === 'points' && courseId.value) await doPoints()
  if (tab.value === 'digest' && lessonId.value) await doDigest()
  if (tab.value === 'quiz' && courseId.value) await doQuiz()
})

watch(tab, async (v) => {
  if (v === 'analysis' && !analysis.value) await loadAnalysis()
  if (v === 'recommend' && !recommend.value.length) await loadRecommend()
})
</script>

<template>
  <div class="page container">
    <div class="section-title">
      <div><h2>AI 学情中心</h2><p>学情分析 · 课程问答 · 知识点 · 智能测验 · 个性化推荐</p></div>
      <RouterLink to="/learning" class="btn btn-soft">我的学习</RouterLink>
    </div>

    <div class="tabs">
      <button v-for="t in tabs" :key="t.id" :class="{on: tab===t.id}" @click="tab=t.id">{{ t.label }}</button>
    </div>

    <div class="picker card" v-if="['ask','points','quiz','digest'].includes(tab)">
      <label>课程
        <select v-model.number="courseId">
          <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.title }}</option>
        </select>
      </label>
      <label>课时（可选）
        <select v-model.number="lessonId">
          <option :value="null">整门课程</option>
          <option v-for="l in lessons" :key="l.id" :value="l.id">{{ l.title }}</option>
        </select>
      </label>
    </div>

    <!-- analysis -->
    <div v-if="tab==='analysis'" class="card box">
      <div class="badges" v-if="analysis">
        <span class="badge">学情摘要</span>
        <span class="badge warn" v-if="analysis.degraded">规则兜底</span>
        <span class="badge ok" v-else>大模型生成</span>
      </div>
      <div v-if="loading && !analysis" class="skeleton" style="height:160px" />
      <template v-else-if="analysis">
        <p class="summary">{{ analysis.summary }}</p>
        <div class="meta">参与课程 {{ analysis.courseCount }} · 完成课时 {{ analysis.completedLessons || 0 }}/{{ analysis.totalLessons || 0 }} · 累计 {{ Math.round((analysis.totalStudiedSeconds||0)/60) }} 分钟</div>
        <div class="cols">
          <div><h4>优势</h4><ul><li v-for="(s,i) in (analysis.strengths||[])" :key="'s'+i">{{ s }}</li></ul></div>
          <div><h4>待改进</h4><ul><li v-for="(s,i) in (analysis.weaknesses||[])" :key="'w'+i">{{ s }}</li></ul></div>
          <div><h4>建议</h4><ul><li v-for="(s,i) in (analysis.suggestions||[])" :key="'g'+i">{{ s }}</li></ul></div>
        </div>
        <div class="plan" v-if="analysis.weeklyPlan?.length">
          <h4>本周计划</h4>
          <ol><li v-for="(s,i) in analysis.weeklyPlan" :key="'p'+i">{{ s }}</li></ol>
        </div>
        <button class="btn btn-soft" style="margin-top:12px" :disabled="loading" @click="loadAnalysis">重新分析</button>
      </template>
    </div>

    <!-- ask -->
    <div v-if="tab==='ask'" class="card box">
      <textarea class="input" rows="4" v-model="askQ" :placeholder="`针对《${selectedCourse?.title || '课程'}》提问…`" />
      <button class="btn btn-primary" style="margin-top:12px" :disabled="loading" @click="doAsk">{{ loading ? '思考中…' : '提问' }}</button>
      <div v-if="askA" class="answer">
        <div class="badges"><span class="badge">回答</span><span class="badge warn" v-if="askA.degraded">规则兜底</span></div>
        <p class="summary">{{ askA.answer }}</p>
      </div>
    </div>

    <!-- points -->
    <div v-if="tab==='points'" class="card box">
      <button class="btn btn-primary" :disabled="loading" @click="doPoints">{{ loading ? '生成中…' : '生成知识点' }}</button>
      <div v-if="points" class="points">
        <div class="badges" style="margin-top:14px">
          <span class="badge">知识点</span>
          <span class="badge warn" v-if="points.degraded">规则兜底</span>
          <span class="badge" v-if="points.cached">缓存</span>
        </div>
        <div v-for="(p,i) in points.points" :key="i" class="point">
          <strong>{{ i + 1 }}. {{ p.title }}</strong>
          <p>{{ p.detail }}</p>
        </div>
      </div>
    </div>

    <!-- quiz -->
    <div v-if="tab==='quiz'" class="card box">
      <div class="quiz-actions">
        <button class="btn btn-primary" :disabled="loading" @click="doQuiz">{{ loading && !quiz ? '出题中…' : '生成测验' }}</button>
        <span class="badge warn" v-if="quiz?.degraded">规则兜底题目</span>
      </div>
      <div v-if="quiz?.questions?.length" class="quiz">
        <div v-for="(q,qi) in quiz.questions" :key="q.id" class="q">
          <h4>{{ qi+1 }}. {{ q.question }}</h4>
          <label v-for="(opt,oi) in q.options" :key="oi" class="opt" :class="{on: answers[q.id]===oi}">
            <input type="radio" :name="q.id" :value="oi" v-model.number="answers[q.id]" />
            <span>{{ opt }}</span>
          </label>
        </div>
        <button class="btn btn-primary" :disabled="loading" @click="doGrade">提交判分</button>
      </div>
      <div v-if="grade" class="grade">
        <h3>得分 {{ grade.score }} / 100（正确 {{ grade.correctCount }}/{{ grade.total }}）</h3>
        <p>{{ grade.feedback }}</p>
        <div v-for="d in grade.detail" :key="d.questionId" class="detail" :class="{ok:d.correct,bad:!d.correct}">
          <strong>{{ d.correct ? '✓' : '✗' }} {{ d.question }}</strong>
          <p>{{ d.explanation }}</p>
        </div>
      </div>
    </div>

    <!-- digest -->
    <div v-if="tab==='digest'" class="card box">
      <button class="btn btn-primary" :disabled="loading || !lessonId" @click="doDigest">{{ loading ? '生成中…' : '生成本课摘要' }}</button>
      <div v-if="digest" style="margin-top:14px">
        <div class="badges">
          <span class="badge">课时摘要</span>
          <span class="badge warn" v-if="digest.degraded">规则兜底</span>
        </div>
        <p class="summary">{{ digest.digest }}</p>
        <h4>关键收获</h4>
        <ul><li v-for="(t,i) in digest.keyTakeaways" :key="i">{{ t }}</li></ul>
      </div>
    </div>

    <!-- recommend -->
    <div v-if="tab==='recommend'">
      <div class="grid">
        <div v-for="(item,i) in recommend" :key="item.course?.id || i" class="rec">
          <CourseCard v-if="item.course" :course="item.course" />
          <CourseCard v-else :course="item" />
          <p class="reason" v-if="item.reason">推荐理由：{{ item.reason }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tabs { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:14px; }
.tabs button {
  border:1px solid var(--ink-200); background:#fff; border-radius:999px; padding:8px 14px;
  cursor:pointer; font-weight:600; color:var(--ink-700);
}
.tabs button.on { background:var(--brand-700); color:#fff; border-color:var(--brand-700); }
.picker { display:flex; gap:16px; flex-wrap:wrap; padding:14px 16px; margin-bottom:14px; }
.picker label { display:flex; flex-direction:column; gap:6px; font-size:13px; color:var(--ink-500); min-width:220px; }
.picker select { padding:8px 10px; border-radius:10px; border:1px solid var(--ink-300); }
.box{padding:22px}
.badges{display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap}
.badge{background:var(--brand-50);color:var(--brand-800);padding:4px 8px;border-radius:999px;font-size:12px;font-weight:700}
.badge.warn{background:#fff7ed;color:#c2410c}
.badge.ok{background:#ecfdf5;color:#047857}
.summary{margin:0;line-height:1.8;font-size:15px;white-space:pre-wrap}
.meta{margin-top:12px;color:var(--ink-500);font-size:13px}
.cols{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-top:18px}
.cols h4,.plan h4{margin:0 0 8px;color:var(--brand-800)}
.cols ul,.plan ol{margin:0;padding-left:18px;color:var(--ink-700);line-height:1.6}
.plan{margin-top:16px;padding-top:12px;border-top:1px solid var(--ink-100)}
.answer{margin-top:16px;padding-top:12px;border-top:1px solid var(--ink-100)}
.point{padding:12px;border-radius:12px;background:var(--ink-50);margin-top:10px}
.point p{margin:6px 0 0;color:var(--ink-600);line-height:1.6}
.quiz-actions{display:flex;gap:10px;align-items:center}
.q{margin:16px 0;padding-bottom:12px;border-bottom:1px solid var(--ink-100)}
.q h4{margin:0 0 10px}
.opt{display:flex;gap:8px;align-items:flex-start;padding:8px 10px;border-radius:10px;cursor:pointer;margin-bottom:6px}
.opt.on,.opt:hover{background:var(--brand-50)}
.grade{margin-top:18px;padding:16px;border-radius:12px;background:#f8fafc}
.detail{margin-top:10px;padding:10px;border-radius:10px}
.detail.ok{background:#ecfdf5}
.detail.bad{background:#fff1f2}
.detail p{margin:6px 0 0;color:var(--ink-600);font-size:13px}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}
.reason{margin:8px 0 0;font-size:13px;color:var(--ink-500)}
@media(max-width:960px){.cols,.grid{grid-template-columns:1fr}}
@media(max-width:640px){
  .box{padding:14px}
  .picker{padding:12px; gap:12px}
  .picker label{min-width:0; width:100%}
  .tabs button{padding:10px 12px; font-size:13px}
  .opt{min-height:44px; align-items:center}
  .quiz-actions{flex-wrap:wrap}
}
</style>
