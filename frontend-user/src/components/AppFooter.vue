<script setup>
import { RouterLink } from 'vue-router'
</script>
<template>
  <footer class="footer">
    <div class="container row">
      <div>
        <strong>知行学堂</strong>
        <p>面向机构与企业的私有化视频学习平台 · 一期演示</p>
      </div>
      <div class="links">
        <RouterLink to="/about">关于我们</RouterLink>
        <RouterLink to="/courses">课程中心</RouterLink>
        <RouterLink to="/news">新闻资讯</RouterLink>
        <span class="meta">本地存储占位 · OSS/CDN 配置预留 · © 2026</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  border-top: 1px solid rgba(15,23,42,.06);
  background: #0b1220; color: #cbd5e1; padding: 28px 0;
}
.row { display:flex; justify-content:space-between; gap: 16px; align-items:end; flex-wrap:wrap; }
strong { color:#fff; font-size: 16px; }
p { margin: 6px 0 0; font-size: 13px; color:#94a3b8; }
.links { display:flex; gap:16px; align-items:center; flex-wrap:wrap; font-size:13px; }
.links a { color:#99f6e4; }
.meta { font-size: 12px; color:#64748b; }
</style>
