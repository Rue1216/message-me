<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <n-result
    status="404"
    title="找不到頁面"
    description="這個網址不存在，或內容已經被刪除了。"
  >
    <template #footer>
      <n-button
        type="primary"
        @click="router.push({ name: 'home' })"
      >
        回到動態牆
      </n-button>
    </template>
  </n-result>
</template>
