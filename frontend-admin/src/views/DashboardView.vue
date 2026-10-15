<script setup>
import { onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import http from '@/api/http'

const stats = ref({})
const chartRef = ref(null)
const trendRef = ref(null)

onMounted(async () => {
  const res = await http.get('/admin/dashboard')
  stats.value = res.data
  const chart = echarts.init(chartRef.value)
  chart.setOption({
    color: ['#0f766e', '#14b8a6', '#f59e0b'],
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['42%', '68%'],
      data: [
        { name: '已发布课程', value: stats.value.publishedCourses || 0 },
        { name: '未发布/其他', value: Math.max((stats.value.courseCount||0)-(stats.value.publishedCourses||0), 0) },
        { name: '结业证书', value: stats.value.certCount || 0 },
      ],
      label: { color: '#334155' }
    }]
  })
  const trend = stats.value.visitTrend || []
  const trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    color: ['#0f766e', '#f59e0b'],
    tooltip: { trigger: 'axis' },
    legend: { data: ['PV', 'UV'] },
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: trend.map(t => t.date) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: 'PV', type: 'line', smooth: true, data: trend.map(t => t.pv) },
      { name: 'UV', type: 'line', smooth: true, data: trend.map(t => t.uv) },
    ]
  })
})
</script>

<template>
  <div>
    <div class="stat-grid">
      <div class="stat-card"><div class="label">注册用户</div><div class="value">{{ stats.userCount || 0 }}</div><div class="hint">启用 {{ stats.enabledUsers || 0 }} · 业务约 3 万 / DID 设计 30 万</div></div>
      <div class="stat-card"><div class="label">课程总数</div><div class="value">{{ stats.courseCount || 0 }}</div><div class="hint">已发布 {{ stats.publishedCourses || 0 }}</div></div>
      <div class="stat-card"><div class="label">学习人次</div><div class="value">{{ stats.learnerCount || 0 }}</div><div class="hint">完成课时 {{ stats.completedLessons || 0 }}</div></div>
      <div class="stat-card"><div class="label">今日访问</div><div class="value">{{ stats.todayPv || 0 }}</div><div class="hint">UV {{ stats.todayUv || 0 }}</div></div>
    </div>
    <div class="page-card" style="margin-bottom:12px">
      <h3 style="margin:0 0 12px">近 14 日访问趋势（PV / UV）</h3>
      <div ref="trendRef" style="height:300px" />
      <p style="margin:8px 0 0;color:#64748b;font-size:12px">数据来自真实 visit_log；若刚部署曲线可能较短。</p>
    </div>
    <div class="page-card">
      <h3 style="margin:0 0 12px">内容结构概览</h3>
      <div ref="chartRef" style="height:320px" />
    </div>
  </div>
</template>
