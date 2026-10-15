<script setup>
import { onMounted, ref } from 'vue'
import http from '@/api/http'
import EmptyState from '@/components/EmptyState.vue'
const list = ref([])
onMounted(async () => {
  const res = await http.get('/certs/mine')
  list.value = res.data
})
</script>
<template>
  <div class="page container">
    <div class="section-title"><div><h2>结业证书</h2><p>完成课程后自动生成证书记录</p></div></div>
    <div v-if="list.length" class="grid">
      <div v-for="c in list" :key="c.id" class="card cert">
        <div class="seal">CERT</div>
        <h3>{{ c.courseTitle }}</h3>
        <p>授予 {{ c.userName }}</p>
        <div class="no">证书编号 {{ c.certNo }}</div>
        <small>颁发时间 {{ c.issuedAt }} · 完成率 {{ Math.round((c.completionRate||0)*100) }}%</small>
      </div>
    </div>
    <EmptyState v-else title="暂无证书" desc="学完课程达到结业比例后将自动颁发" icon="🎓" />
  </div>
</template>
<style scoped>
.grid{display:grid;grid-template-columns:repeat(2,1fr);gap:16px}
.cert{padding:24px;position:relative;background:linear-gradient(135deg,#fff,#f0fdfa)}
.seal{position:absolute;right:18px;top:18px;width:48px;height:48px;border-radius:50%;display:grid;place-items:center;border:2px solid var(--brand-600);color:var(--brand-700);font-weight:800;font-size:11px}
h3{margin:0 0 8px;font-size:20px}
p{margin:0 0 14px;color:var(--ink-500)}
.no{font-family:ui-monospace,monospace;background:var(--ink-50);padding:8px 10px;border-radius:8px;display:inline-block;margin-bottom:10px}
@media(max-width:900px){.grid{grid-template-columns:1fr}}
</style>
