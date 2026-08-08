<script setup lang="ts">
import { cn } from '@/lib/utils'

/** 小型標記。用於標籤、計數等短資訊。 */
defineProps<{ class?: string }>()
</script>

<template>
  <span
    :class="
      cn(
        'inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground',
        $props.class,
      )
    "
  >
    <slot />
  </span>
</template>
