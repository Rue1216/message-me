<script setup lang="ts">
// 骨架階段的佔位頁面。實際的版面、路由與狀態管理於 feat/frontend-foundation 建立。
const appName = 'Message Me'
</script>

<template>
  <main class="placeholder">
    <h1>{{ appName }}</h1>
    <p>簡易社群媒體平台 — 前端骨架已就緒。</p>
  </main>
</template>

<style scoped>
.placeholder {
  font-family: system-ui, -apple-system, 'Segoe UI', 'Noto Sans TC', sans-serif;
  margin: 4rem auto;
  max-width: 40rem;
  padding: 0 1.5rem;
  text-align: center;
}
</style>
