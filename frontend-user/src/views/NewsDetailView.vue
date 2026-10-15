<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import http from '@/api/http'
const route = useRoute()
const news = ref(null)
onMounted(async () => {
  const res = await http.get(`/public/news/${route.params.id}`)
  news.value = res.data
})
</script>
<template>
  <div class="page container" v-if="news">
    <RouterLink to="/news" class="back">← 返回列表</RouterLink>
    <article class="card article">
      <h1>{{ news.title }}</h1>
      <div class="meta">发布时间 {{ news.publishedAt || news.createdAt }} · 阅读 {{ news.viewCount || 0 }}</div>
      <div class="content" v-html="news.content" />
    </article>
  </div>
</template>
<style scoped>
.back { color: var(--brand-700); font-weight:600; font-size:14px; }
.article { margin-top:12px; padding: 28px; }
h1 { margin:0 0 10px; font-size:28px; letter-spacing:-.02em; }
.meta { color:var(--ink-500); margin-bottom: 20px; font-size:13px; }
.content { line-height:1.8; color:var(--ink-700); }
</style>
