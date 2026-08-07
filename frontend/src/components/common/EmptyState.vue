<script setup lang="ts">
/**
 * 空狀態。
 *
 * <p>好的空狀態不只是說「沒有資料」，還會告訴使用者接下來能做什麼——
 * 因此除了說明文字，也留了一個放行動按鈕的插槽。
 */
defineProps<{ title: string; description?: string }>()
</script>

<template>
  <div class="flex flex-col items-center gap-2 rounded-lg border border-dashed border-border px-6 py-12 text-center">
    <p class="font-medium">
      {{ title }}
    </p>
    <p
      v-if="description"
      class="text-sm text-muted-foreground"
    >
      {{ description }}
    </p>
    <div
      v-if="$slots.action"
      class="mt-2"
    >
      <slot name="action" />
    </div>
  </div>
</template>
