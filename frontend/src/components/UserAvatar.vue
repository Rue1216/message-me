<script setup lang="ts">
import { computed } from 'vue'

/**
 * 使用者頭像。
 *
 * 沒有上傳圖片時退回顯示名稱的第一個字，避免版面出現空白的圓形。
 */
const props = withDefaults(
  defineProps<{
    name: string
    image?: string | null
    size?: number
  }>(),
  { image: null, size: 40 },
)

/** 取第一個字元；以展開運算子切割，避免把 emoji 之類的代理對拆成半個字。 */
const initial = computed(() => [...props.name.trim()][0] ?? '?')
</script>

<template>
  <!-- alt 必須經 img-props 傳遞：直接寫 alt 只會落在外層容器，不會進到 <img> -->
  <n-avatar
    v-if="image"
    round
    :size="size"
    :src="image"
    :img-props="{ alt: `${name} 的頭像` }"
  />
  <n-avatar
    v-else
    round
    :size="size"
    color="#3f6ad8"
  >
    {{ initial }}
  </n-avatar>
</template>
