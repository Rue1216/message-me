<script setup lang="ts">
import { Toaster } from 'vue-sonner'
import 'vue-sonner/style.css'

import AppShell from '@/components/layout/AppShell.vue'
import { useTheme } from '@/composables/useTheme'

/**
 * 應用程式根元件。
 *
 * <p>相較於改版前，這裡少了一整層 provider：Naive UI 需要 config / message / dialog
 * 三個 provider 才能運作，而 Tailwind 沒有執行期的主題環境，
 * 提示訊息也改由 vue-sonner 以單一 Toaster 出口處理。
 *
 * <p>實際版面在 AppShell，讓它能在測試中單獨掛載。
 */
const { isDark } = useTheme()
</script>

<template>
  <AppShell />

  <!--
    全站唯一的提示出口。richColors 讓成功與錯誤有不同的底色，
    theme 跟著目前的主題走，深色模式下才不會出現一塊刺眼的白。
  -->
  <Toaster
    :theme="isDark ? 'dark' : 'light'"
    position="top-center"
    rich-colors
    close-button
  />
</template>
