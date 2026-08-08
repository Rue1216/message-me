<script setup lang="ts">
import { cn } from '@/lib/utils'

/** 內容卡片。發文、表單、留言區共用的容器。 */
withDefaults(defineProps<{ class?: string; as?: string }>(), { as: 'div' })
</script>

<template>
  <component
    :is="as"
    :class="cn('rounded-lg border border-border bg-card text-card-foreground shadow-sm', $props.class)"
  >
    <slot />
  </component>
</template>
