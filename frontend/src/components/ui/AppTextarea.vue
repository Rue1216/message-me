<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'

import { cn } from '@/lib/utils'

/**
 * 多行輸入框，內容變長時自動增高。
 *
 * <p>高度以 rows 屬性調整而非 inline style。這與專案全面採用 Tailwind 的一致性原則一致
 * （見 eslint.config.ts 對 :style 的限制），也讓實作簡單得多：
 * 只需要數換行符號，不必量測 scrollHeight，因此不會觸發額外的版面重排。
 * 代價是高度以「行」為單位而非像素——對輸入框而言，這個粒度綽綽有餘。
 */
const props = withDefaults(
  defineProps<{
    class?: string
    invalid?: boolean
    minRows?: number
    maxRows?: number
    autosize?: boolean
  }>(),
  { minRows: 3, maxRows: 12, autosize: true },
)

const model = defineModel<string>({ default: '' })

const rows = ref(props.minRows)

function recalculate(): void {
  if (!props.autosize) {
    return
  }
  const lineCount = model.value.split('\n').length
  rows.value = Math.min(Math.max(lineCount, props.minRows), props.maxRows)
}

watch(model, () => void nextTick(recalculate))
onMounted(recalculate)
</script>

<template>
  <textarea
    v-model="model"
    :rows="rows"
    :aria-invalid="invalid || undefined"
    :class="
      cn(
        'flex w-full resize-y rounded-md border border-input bg-card px-3 py-2 text-sm transition-colors',
        'placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50',
        'aria-invalid:border-destructive',
        $props.class,
      )
    "
  />
</template>
