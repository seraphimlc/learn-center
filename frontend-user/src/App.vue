<script setup>
import { watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import { toasts } from '@/utils/toast'
import http from '@/api/http'

const route = useRoute()
function visitorKey() {
  let k = localStorage.getItem('vlp_vid')
  if (!k) {
    k = 'v_' + Math.random().toString(36).slice(2) + Date.now().toString(36)
    localStorage.setItem('vlp_vid', k)
  }
  return k
}
function track(path) {
  http.post('/public/visit', { path, visitorKey: visitorKey() }).catch(() => {})
}
watch(() => route.fullPath, (p) => track(p), { immediate: true })
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main>
      <RouterView />
    </main>
    <AppFooter />
    <div class="toast-host">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="t.type">{{ t.message }}</div>
    </div>
  </div>
</template>

<style scoped>
.app-shell { min-height: 100vh; display: flex; flex-direction: column; }
main { flex: 1; }
</style>
