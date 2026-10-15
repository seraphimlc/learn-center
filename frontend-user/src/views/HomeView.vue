<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import http from '@/api/http'
import CourseCard from '@/components/CourseCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { mediaUrl } from '@/utils/toast'

const loading = ref(true)
const data = ref({ banners: [], hotCourses: [], recommendCourses: [], news: [], categories: [], continueLearning: [] })
const bannerIdx = ref(0)

onMounted(async () => {
  try {
    const res = await http.get('/public/home')
    data.value = res.data
  } finally { loading.value = false }
  setInterval(() => {
    if (data.value.banners?.length) bannerIdx.value = (bannerIdx.value + 1) % data.value.banners.length
  }, 5000)
})
</script>

<template>
  <div class="page">
    <section class="hero container">
      <div class="hero-card card" v-if="data.banners?.length">
        <div class="slide" :style="{ backgroundImage: `linear-gradient(120deg, rgba(15,23,42,.55), rgba(15,118,110,.35)), url(${mediaUrl(data.banners[bannerIdx].imageUrl)})` }">
          <div class="copy">
            <span class="badge">精选运营位</span>
            <h1>{{ data.banners[bannerIdx].title }}</h1>
            <p>沉浸式视频学习 · 进度自动同步 · AI 学情洞察</p>
            <RouterLink class="btn btn-primary" :to="data.banners[bannerIdx].linkUrl || '/courses'">立即学习</RouterLink>
          </div>
        </div>
        <div class="dots">
          <button v-for="(b,i) in data.banners" :key="b.id" :class="{on:i===bannerIdx}" @click="bannerIdx=i" />
        </div>
      </div>
      <div class="side">
        <div class="card panel">
          <h3>分类快捷入口</h3>
          <div class="cats">
            <RouterLink v-for="c in data.categories" :key="c.id" :to="`/courses?categoryId=${c.id}`" class="cat">{{ c.name }}</RouterLink>
          </div>
        </div>
        <div class="card panel">
          <h3>最新资讯</h3>
          <ul class="news">
            <li v-for="n in data.news" :key="n.id">
              <RouterLink :to="`/news/${n.id}`">{{ n.title }}</RouterLink>
            </li>
          </ul>
          <RouterLink to="/news" class="more">查看全部 →</RouterLink>
        </div>
      </div>
    </section>

    <section class="container" style="margin-top:28px" v-if="data.continueLearning?.length">
      <div class="section-title"><div><h2>继续学习</h2><p>从上次离开的位置接着看</p></div></div>
      <div class="continue">
        <RouterLink v-for="item in data.continueLearning" :key="item.progress.id"
          class="card cont" :to="`/play/${item.progress.courseId}/${item.progress.lessonId}`">
          <strong>{{ item.course?.title }}</strong>
          <span>{{ item.lesson?.title }} · 已到 {{ item.progress.positionSeconds }}s</span>
        </RouterLink>
      </div>
    </section>

    <section class="container" style="margin-top:32px">
      <div class="section-title"><div><h2>热门课程</h2><p>学员关注度最高的内容</p></div><RouterLink to="/courses">全部课程</RouterLink></div>
      <div v-if="loading" class="grid"><div v-for="i in 4" :key="i" class="skeleton" style="height:260px" /></div>
      <div v-else-if="data.hotCourses?.length" class="grid">
        <CourseCard v-for="c in data.hotCourses" :key="c.id" :course="c" />
      </div>
      <EmptyState v-else title="暂无热门课程" desc="管理员上架课程后将展示在这里" icon="🔥" />
    </section>

    <section class="container" style="margin-top:32px">
      <div class="section-title"><div><h2>为你推荐</h2><p>精选与运营推荐位课程</p></div></div>
      <div class="grid">
        <CourseCard v-for="c in data.recommendCourses" :key="c.id" :course="c" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero { display:grid; grid-template-columns: 1.6fr .9fr; gap: 18px; }
.hero-card { overflow:hidden; position:relative; min-height: 340px; }
.slide {
  min-height: 340px; background-size:cover; background-position:center;
  display:flex; align-items:end; padding: 36px;
}
.copy { color:#fff; max-width: 520px; }
.copy h1 { margin: 12px 0 8px; font-size: 36px; letter-spacing: -.03em; line-height:1.15; }
.copy p { margin: 0 0 18px; color: rgba(255,255,255,.85); }
.dots { position:absolute; right: 18px; bottom: 18px; display:flex; gap:6px; }
.dots button { width:8px; height:8px; border-radius:999px; border:none; background:rgba(255,255,255,.45); cursor:pointer; }
.dots button.on { width:22px; background:#fff; }
.side { display:flex; flex-direction:column; gap: 18px; }
.panel { padding: 18px; }
.panel h3 { margin: 0 0 12px; font-size: 15px; }
.cats { display:flex; flex-wrap:wrap; gap:8px; }
.cat {
  padding: 8px 12px; border-radius: 999px; background: var(--brand-50); color: var(--brand-800);
  font-size: 13px; font-weight: 600;
}
.news { list-style:none; margin:0; padding:0; display:flex; flex-direction:column; gap:10px; }
.news a { font-size: 14px; line-height:1.4; color: var(--ink-700); }
.news a:hover { color: var(--brand-700); }
.more { display:inline-block; margin-top: 12px; color: var(--brand-700); font-size: 13px; font-weight:600; }
.grid { display:grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.continue { display:grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.cont { padding: 14px 16px; display:flex; flex-direction:column; gap:6px; }
.cont span { color: var(--ink-500); font-size: 13px; }
@media (max-width: 960px) {
  .hero, .grid, .continue { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .hero-card, .slide { min-height: 220px; }
  .slide { padding: 20px 16px; }
  .copy h1 { font-size: 24px; }
  .copy p { font-size: 14px; margin-bottom: 14px; }
  .panel { padding: 14px; }
}
</style>
