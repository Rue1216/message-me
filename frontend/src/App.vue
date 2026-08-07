<script setup lang="ts">
import { dateZhTW, zhTW } from 'naive-ui'

import AppShell from '@/components/AppShell.vue'
import { themeOverrides } from '@/theme'

/**
 * 應用程式根元件。
 *
 * 這裡只做一件事：把整棵樹包進 Naive UI 的環境中——繁體中文語系、主題覆寫，
 * 以及 message / dialog 兩個必須由 provider 注入的全域 API。
 * 實際版面在 AppShell，讓它能在測試中單獨掛載。
 */
</script>

<template>
  <n-config-provider
    :locale="zhTW"
    :date-locale="dateZhTW"
    :theme-overrides="themeOverrides"
  >
    <n-global-style />
    <n-message-provider :max="3">
      <n-dialog-provider>
        <AppShell />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>
