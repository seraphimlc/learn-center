<script setup>
import { onMounted, ref } from 'vue'
import http from '@/api/http'
import EmptyState from '@/components/EmptyState.vue'
const list = ref([])
onMounted(async () => {
  const res = await http.get('/notifications', { params: { page: 1, size: 50 } })
  list.value = res.data.list
})
async function read(n) {
  await http.post(`/notifications/${n.id}/read`)
  n.readFlag = true
}
</script>
<template>
  <div class="page container">
    <div class="section-title"><div><h2>消息中心</h2><p>系统通知、公告与学情预警</p></div></div>
    <div v-if="list.length" class="list">
      <div v-for="n in list" :key="n.id" class="card item" :class="{unread: !n.readFlag}" @click="read(n)">
        <div class="top"><strong>{{ n.title }}</strong><span>{{ n.type }}</span></div>
        <p>{{ n.content }}</p>
        <small>{{ n.createdAt }}</small>
      </div>
    </div>
    <EmptyState v-else title="暂无消息" desc="公告与预警会展示在这里" icon="🔔" />
  </div>
</template>
<style scoped>
.list{display:flex;flex-direction:column;gap:10px}
.item{padding:16px 18px;cursor:pointer}
.item.unread{border-color: var(--brand-200); background: linear-gradient(180deg,#fff,var(--brand-50))}
.top{display:flex;justify-content:space-between;gap:12px}
.top span{font-size:12px;color:var(--brand-700);font-weight:700}
p{margin:8px 0;color:var(--ink-700);line-height:1.6}
small{color:var(--ink-500)}
</style>
