<script setup lang="ts">
import { RouterLink } from 'vue-router'

/**
 * 單一標籤，點擊後前往該標籤的發文列表。
 *
 * <p>顯示時補上 `#`，但路由參數只帶名稱本身——`#` 是呈現用的裝飾，
 * 不是資料的一部分（後端存的也不含它）。
 */
defineProps<{ name: string }>()
</script>

<template>
  <RouterLink
    :to="{ name: 'tag', params: { name } }"
    class="inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground transition-colors hover:bg-primary/10 hover:text-primary"
  >
    #{{ name }}
  </RouterLink>
</template>
