<script setup lang="ts">
import { cn } from '@/lib/utils'

/**
 * 載入中的內容佔位。
 *
 * <p>比轉圈圖示好的地方在於它保留了內容的形狀：版面在資料到達前後不會位移，
 * 使用者也能預期即將出現什麼。
 *
 * <p>`aria-hidden` 是刻意的——骨架對螢幕閱讀器沒有意義，載入狀態由容器上的
 * `aria-busy` 表達，否則使用者會聽到一串沒有內容的空元素。
 */
defineProps<{ class?: string }>()
</script>

<template>
  <div
    :class="cn('animate-pulse rounded-md bg-muted', $props.class)"
    aria-hidden="true"
  />
</template>
