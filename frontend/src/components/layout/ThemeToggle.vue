<script setup lang="ts">
import { Moon, Sun } from '@lucide/vue'

import AppButton from '@/components/ui/AppButton.vue'
import { useTheme } from '@/composables/useTheme'

/**
 * 深色模式切換。
 *
 * <p>圖示標為 aria-hidden，語意由 aria-label 承載——螢幕閱讀器需要的是
 * 「切換到深色模式」這個動作描述，而不是「太陽」這個形狀。
 */
const { isDark, toggle } = useTheme()
</script>

<template>
  <AppButton
    variant="ghost"
    size="icon"
    :aria-label="isDark ? '切換至淺色模式' : '切換至深色模式'"
    :aria-pressed="isDark"
    @click="toggle"
  >
    <Moon
      v-if="isDark"
      class="size-5"
      aria-hidden="true"
    />
    <Sun
      v-else
      class="size-5"
      aria-hidden="true"
    />
  </AppButton>
</template>
