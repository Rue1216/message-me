<script setup lang="ts">
import { computed } from 'vue'

import { cn } from '@/lib/utils'

/**
 * 使用者頭像。
 *
 * <p>沒有上傳圖片時退回顯示名稱的第一個字，避免版面出現空白的圓形。
 *
 * <p>尺寸以具名選項（sm / md / lg / xl）對應 Tailwind 的 size-* class，而非接受任意像素數值。
 * 這讓全站的頭像收斂成四種尺寸，版面因此一致；改版前的 :size="40" 這類寫法，
 * 久了必然會出現 38、42 這種只差幾像素、卻沒有人說得出理由的數值。
 */
const props = withDefaults(
  defineProps<{
    name: string
    image?: string | null
    size?: 'sm' | 'md' | 'lg' | 'xl'
  }>(),
  { image: null, size: 'md' },
)

const SIZE_CLASSES = {
  sm: 'size-7 text-xs',
  md: 'size-10 text-sm',
  lg: 'size-14 text-lg',
  xl: 'size-20 text-2xl',
} as const

/** 取第一個字元；以展開運算子切割，避免把 emoji 之類的代理對拆成半個字。 */
const initial = computed(() => [...props.name.trim()][0] ?? '?')
</script>

<template>
  <img
    v-if="image"
    :src="image"
    :alt="`${name} 的頭像`"
    loading="lazy"
    decoding="async"
    :class="cn('shrink-0 rounded-full object-cover', SIZE_CLASSES[size])"
  >
  <!--
    退回字母時整塊視為裝飾：使用者名稱就在旁邊，讓螢幕閱讀器再念一次同一個字沒有意義。
  -->
  <span
    v-else
    :class="
      cn(
        'inline-flex shrink-0 select-none items-center justify-center rounded-full bg-primary font-medium text-primary-foreground',
        SIZE_CLASSES[size],
      )
    "
    aria-hidden="true"
  >{{ initial }}</span>
</template>
