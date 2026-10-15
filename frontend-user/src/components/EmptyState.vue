<script setup>
defineProps({ title: String, desc: String, icon: { type: String, default: '📭' } })
</script>
<template>
  <div class="empty card">
    <div class="icon">{{ icon }}</div>
    <h3>{{ title }}</h3>
    <p>{{ desc }}</p>
    <slot />
  </div>
</template>
<style scoped>
h3 { margin: 0 0 6px; color: var(--ink-900); }
p { margin: 0; }
</style>
