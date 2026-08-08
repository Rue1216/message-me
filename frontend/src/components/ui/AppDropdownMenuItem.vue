<script setup lang="ts">
import { DropdownMenuItem } from 'reka-ui'

/**
 * 動作選單裡的一個選項。
 *
 * <p>樣式集中在這裡，使用端只指定語意（`variant="destructive"`），
 * 不必記得刪除該配哪幾個顏色 class——與 AppButton 的分工相同。
 *
 * <p>高亮走 Reka 的 data-highlighted 而非 :hover：鍵盤與滑鼠移動的效果因此一致，
 * 用方向鍵選到的那一項看起來就跟滑鼠指著的一樣。
 */
withDefaults(defineProps<{ variant?: 'default' | 'destructive' }>(), { variant: 'default' })

defineEmits<{ select: [] }>()
</script>

<template>
  <DropdownMenuItem
    :class="[
      'flex cursor-pointer select-none items-center gap-2 rounded-sm px-2 py-1.5 text-sm transition-colors focus:outline-none data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
      variant === 'destructive'
        ? 'text-destructive data-[highlighted]:bg-destructive/10'
        : 'data-[highlighted]:bg-muted',
    ]"
    @select="$emit('select')"
  >
    <slot />
  </DropdownMenuItem>
</template>
