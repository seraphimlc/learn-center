<script setup>
import { RouterLink } from 'vue-router'
import { formatDuration, mediaUrl } from '@/utils/toast'
defineProps({ course: { type: Object, required: true } })
</script>

<template>
  <RouterLink :to="`/courses/${course.id}`" class="course-card card">
    <div class="cover" :style="{ backgroundImage: `linear-gradient(135deg, rgba(15,118,110,.85), rgba(15,23,42,.75)), url(${mediaUrl(course.coverUrl)})` }">
      <span v-if="course.hot" class="tag">热门</span>
      <span v-if="course.recommended" class="tag rec">推荐</span>
      <div class="dur">{{ formatDuration(course.durationSeconds) }}</div>
    </div>
    <div class="body">
      <h3>{{ course.title }}</h3>
      <p>{{ course.summary || '暂无简介' }}</p>
      <div class="meta">
        <span>{{ course.lessonCount || 0 }} 课时</span>
        <span>{{ course.learnerCount || 0 }} 人在学</span>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.course-card { overflow:hidden; display:block; transition: .2s ease; }
.course-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-lg); }
.cover {
  height: 148px; background-size: cover; background-position: center; position:relative;
  display:flex; align-items:flex-end; padding: 12px;
}
.tag {
  position:absolute; top: 12px; left: 12px; background: #f59e0b; color:#111; font-size:11px;
  font-weight:700; padding: 3px 8px; border-radius: 999px;
}
.tag.rec { left: auto; right: 12px; background: #fff; color: var(--brand-800); }
.dur { color:#fff; font-size: 12px; font-weight: 600; background: rgba(0,0,0,.35); padding: 4px 8px; border-radius: 999px; }
.body { padding: 14px 16px 16px; }
h3 { margin:0 0 8px; font-size: 16px; line-height: 1.35; }
p {
  margin:0 0 12px; color: var(--ink-500); font-size: 13px; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow:hidden; min-height: 39px;
}
.meta { display:flex; justify-content:space-between; color: var(--ink-500); font-size: 12px; }
</style>
