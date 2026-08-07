<script setup lang="ts">
import { cva, type VariantProps } from 'class-variance-authority'
import { computed } from 'vue'

import { cn } from '@/lib/utils'

/**
 * 靜態提示區塊（非彈出式）。用於空狀態說明、載入失敗、表單層級的錯誤。
 *
 * <p>錯誤變體使用 `role="alert"`，讓螢幕閱讀器在內容出現時立即朗讀；
 * 一般提示則不搶焦點。
 */
const alertVariants = cva('rounded-md border px-4 py-3 text-sm', {
  variants: {
    variant: {
      default: 'border-border bg-muted text-foreground',
      error: 'border-destructive/40 bg-destructive/10 text-destructive',
    },
  },
  defaultVariants: { variant: 'default' },
})

const props = withDefaults(
  defineProps<{ variant?: VariantProps<typeof alertVariants>['variant']; class?: string }>(),
  { variant: 'default' },
)

const role = computed(() => (props.variant === 'error' ? 'alert' : 'status'))
</script>

<template>
  <div
    :class="cn(alertVariants({ variant: props.variant }), props.class)"
    :role="role"
  >
    <slot />
  </div>
</template>
