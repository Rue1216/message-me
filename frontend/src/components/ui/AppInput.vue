<script setup lang="ts">
import { cn } from '@/lib/utils'

/**
 * 單行輸入框。
 *
 * <p>`aria-invalid` 由使用端在驗證失敗時設定，樣式再依它變色——
 * 錯誤狀態因此同時傳達給看得見的人與螢幕閱讀器，不會只有紅框而沒有語意。
 */
defineProps<{ class?: string; invalid?: boolean }>()

const model = defineModel<string>({ default: '' })
</script>

<template>
  <input
    v-model="model"
    :aria-invalid="invalid || undefined"
    :class="
      cn(
        'flex h-10 w-full rounded-md border border-input bg-card px-3 py-2 text-sm transition-colors',
        'placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50',
        'aria-invalid:border-destructive',
        $props.class,
      )
    "
  >
</template>
