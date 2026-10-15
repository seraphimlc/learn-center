<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '@/api/http'
import CourseCard from '@/components/CourseCard.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const categories = ref([])
const list = ref([])
const total = ref(0)
const loading = ref(true)
const page = ref(1)
const keyword = ref(route.query.keyword || '')
const categoryId = ref(route.query.categoryId ? Number(route.query.categoryId) : null)

async function load() {
  loading.value = true
  try {
    const res = await http.get('/public/courses', { params: { page: page.value, size: 12, keyword: keyword.value || undefined, categoryId: categoryId.value || undefined } })
    list.value = res.data.list
    total.value = res.data.total
  } finally { loading.value = false }
}

function applyFilter(id) {
  categoryId.value = id
  page.value = 1
  router.replace({ query: { ...route.query, categoryId: id || undefined, keyword: keyword.value || undefined } })
  load()
}

function search() {
  page.value = 1
  router.replace({ query: { ...route.query, keyword: keyword.value || undefined } })
  load()
}

onMounted(async () => {
  const cats = await http.get('/public/categories')
  categories.value = cats.data
  await load()
})
watch(() => route.query, () => {
  keyword.value = route.query.keyword || ''
  categoryId.value = route.query.categoryId ? Number(route.query.categoryId) : null
  load()
})
</script>

<template>
  <div class="page container">
    <div class="section-title">
      <div><h2>课程中心</h2><p>按分类浏览，或搜索你感兴趣的主题</p></div>
      <form class="search-bar" @submit.prevent="search">
        <input class="input" v-model="keyword" placeholder="输入关键词…" style="width:240px" />
        <button class="btn btn-primary" type="submit">搜索</button>
      </form>
    </div>
    <div class="filters">
      <button :class="['chip', {on: !categoryId}]" @click="applyFilter(null)">全部</button>
      <button v-for="c in categories" :key="c.id" :class="['chip', {on: categoryId===c.id}]" @click="applyFilter(c.id)">{{ c.name }}</button>
    </div>
    <div v-if="loading" class="grid"><div v-for="i in 8" :key="i" class="skeleton" style="height:250px"/></div>
    <div v-else-if="list.length" class="grid"><CourseCard v-for="c in list" :key="c.id" :course="c" /></div>
    <EmptyState v-else title="没有找到课程" desc="试试更换关键词或分类" icon="🔍" />
    <div class="pager" v-if="total > 12">
      <button class="btn btn-ghost" :disabled="page<=1" @click="page--; load()">上一页</button>
      <span>第 {{ page }} 页 · 共 {{ total }} 门</span>
      <button class="btn btn-ghost" :disabled="page*12>=total" @click="page++; load()">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.search-bar { display:flex; gap:8px; }
.filters { display:flex; flex-wrap:wrap; gap:8px; margin-bottom: 18px; }
.chip { border:1px solid var(--ink-300); background:#fff; border-radius:999px; padding:8px 14px; cursor:pointer; font-weight:600; color:var(--ink-700); }
.chip.on { background: var(--brand-700); color:#fff; border-color: transparent; }
.grid { display:grid; grid-template-columns: repeat(4,1fr); gap:16px; }
.pager { display:flex; justify-content:center; align-items:center; gap:16px; margin-top:24px; color:var(--ink-500); }
@media (max-width:960px){ .grid{grid-template-columns:1fr 1fr} }
@media (max-width:640px){
  .grid{grid-template-columns:1fr}
  .search-bar{width:100%}
  .search-bar .input{width:100% !important; flex:1}
  .pager{flex-wrap:wrap; gap:10px; font-size:13px}
}
</style>
