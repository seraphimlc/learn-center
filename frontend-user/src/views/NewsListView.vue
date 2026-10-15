<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import http from '@/api/http'
import EmptyState from '@/components/EmptyState.vue'

const list = ref([])
const loading = ref(true)
onMounted(async () => {
  try {
    const res = await http.get('/public/news', { params: { page: 1, size: 20 } })
    list.value = res.data.list
  } finally { loading.value = false }
})
</script>
<template>
  <div class="page container">
    <div class="section-title"><div><h2>新闻资讯</h2><p>平台动态与学习资讯</p></div></div>
    <div v-if="loading" class="skeleton" style="height:200px" />
    <div v-else-if="list.length" class="list">
      <RouterLink v-for="n in list" :key="n.id" :to="`/news/${n.id}`" class="card item">
        <h3>{{ n.title }}</h3>
        <p>{{ n.summary }}</p>
        <small>{{ n.publishedAt || n.createdAt }}</small>
      </RouterLink>
    </div>
    <EmptyState v-else title="暂无新闻" desc="后台发布后将出现在这里" icon="📰" />
  </div>
</template>
<style scoped>
.list { display:flex; flex-direction:column; gap:12px; }
.item { padding: 18px 20px; display:block; }
.item:hover { border-color: var(--brand-200); }
h3 { margin:0 0 8px; font-size:18px; }
p { margin:0 0 10px; color:var(--ink-500); line-height:1.6; }
small { color:var(--ink-500); }
</style>
