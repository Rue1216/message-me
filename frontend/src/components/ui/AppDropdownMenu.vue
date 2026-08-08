<script setup lang="ts">
import { MoreHorizontal } from '@lucide/vue'
import {
  DropdownMenuContent,
  DropdownMenuPortal,
  DropdownMenuRoot,
  DropdownMenuTrigger,
} from 'reka-ui'

import { computed } from 'vue'

import { cn } from '@/lib/utils'

/**
 * 以「⋯」按鈕開啟的動作選單。
 *
 * <p>建立在 Reka UI 之上而非自己刻：選單的鍵盤與焦點行為比外觀複雜得多——
 * 方向鍵在選項間移動、Esc 關閉、關閉後焦點回到觸發鍵、點擊外部或捲動時關閉、
 * 以及 aria-haspopup 與 aria-expanded 的狀態同步。自己實作幾乎必定會漏掉幾項。
 *
 * <p>內容以 Portal 掛到 body：選單若留在原地，會被祖先的 overflow 或
 * stacking context 裁切——留言列表這種巢狀又會捲動的版面尤其容易踩到。
 *
 * <p>`label` 是必填而非選填：觸發鍵只有圖示沒有文字，少了它螢幕閱讀器只會念「按鈕」。
 */
const props = defineProps<{ label: string; class?: string }>()

const triggerClasses = computed(() =>
  cn(
    'inline-flex size-8 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground data-[state=open]:bg-muted data-[state=open]:text-foreground',
    props.class,
  ),
)
</script>

<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger
      :aria-label="label"
      :class="triggerClasses"
    >
      <MoreHorizontal
        class="size-4"
        aria-hidden="true"
      />
    </DropdownMenuTrigger>

    <DropdownMenuPortal>
      <DropdownMenuContent
        align="end"
        :side-offset="4"
        class="z-50 min-w-36 rounded-md border border-border bg-card p-1 shadow-lg focus:outline-none"
      >
        <slot />
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>
