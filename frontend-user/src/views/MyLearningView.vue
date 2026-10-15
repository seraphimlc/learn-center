<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import http from '@/api/http'
import EmptyState from '@/components/EmptyState.vue'

const list = ref([])
const loading = ref(true)

function continueTo(item) {
  const courseId = item.course?.id || item.lastProgress?.courseId
  const lessonId = item.continueLessonId || item.lastProgress?.lessonId
  return `/play/${courseId}/${lessonId}`
}

onMounted(async () => {
  try {
    const res = await http.get('/learn/my')
    list.value = res.data
  } finally { loading.value = false }
})
</script>
<template>
  <div class="page container">
    <div class="section-title"><div><h2>我的学习</h2><p>按课程汇总完成课时；继续学习会跳到下一节未完成课时</p></div>
      <RouterLink to="/certs" class="btn btn-soft">我的证书</RouterLink>
    </div>
    <div v-if="loading" class="skeleton" style="height:180px" />
    <div v-else-if="list.length" class="grid">
      <div v-for="item in list" :key="item.course?.id || item.lastProgress?.id" class="card row">
        <div class="main">
          <h3>{{ item.course?.title }}</h3>
          <p>
            <template v-if="item.continueCompleted">全部课时已完成</template>
            <template v-else>下一节：{{ item.lesson?.title || '—' }}</template>
          </p>
          <div class="bar"><i :style="{width: Math.min(100, item.progressPercent||0)+'%'}" /></div>
          <small>完成 {{ item.completedLessons }}/{{ item.totalLessons }} 课时 · {{ Math.round(item.progressPercent||0) }}%</small>
        </div>
        <RouterLink class="btn btn-primary" :to="continueTo(item)">
          {{ item.continueCompleted ? '再看一遍' : '继续学习' }}
        </RouterLink>
      </div>
    </div>
    <EmptyState v-else title="还没有学习记录" desc="去课程中心挑一门课开始吧" icon="📚">
      <RouterLink to="/courses" class="btn btn-primary" style="margin-top:12px">浏览课程</RouterLink>
    </EmptyState>
  </div>
</template>
<style scoped>
.grid { display:flex; flex-direction:column; gap:12px; }
.row { padding:18px 20px; display:flex; justify-content:space-between; gap:16px; align-items:center; }
.main { flex:1; min-width:0; }
h3 { margin:0 0 6px; }
p { margin:0 0 10px; color:var(--ink-500); font-size:14px; }
.bar { height:8px; background:var(--ink-100); border-radius:999px; overflow:hidden; width: min(360px, 100%); }
.bar i { display:block; height:100%; background: linear-gradient(90deg, var(--brand-500), var(--brand-800)); }
small { color:var(--ink-500); }
</style>
