<script setup lang="ts">
import {
  DialogContent,
  DialogDescription,
  DialogOverlay,
  DialogPortal,
  DialogRoot,
  DialogTitle,
} from 'reka-ui'

/**
 * 對話框。
 *
 * <p>建立在 Reka UI 之上而非自己刻：對話框的無障礙細節比看起來多得多——
 * 焦點鎖在框內、Esc 關閉、開啟時把背景內容標為 inert、關閉後焦點回到觸發元素、
 * aria-modal 與標題的關聯。這些自己實作幾乎必定會漏掉幾項。
 *
 * <p>`title` 是必填而非選填：Reka UI 要求每個對話框都有可存取的名稱，
 * 少了它螢幕閱讀器只會念出「dialog」。
 */
defineProps<{ title: string; description?: string }>()

const open = defineModel<boolean>('open', { required: true })
</script>

<template>
  <DialogRoot v-model:open="open">
    <DialogPortal>
      <DialogOverlay
        class="fixed inset-0 z-50 bg-black/50 data-[state=open]:animate-in data-[state=open]:fade-in"
      />
      <DialogContent
        class="fixed left-1/2 top-1/2 z-50 w-[calc(100vw-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 rounded-lg border border-border bg-card p-5 shadow-lg focus:outline-none"
      >
        <DialogTitle class="text-base font-semibold">
          {{ title }}
        </DialogTitle>
        <DialogDescription
          v-if="description"
          class="mt-1 text-sm text-muted-foreground"
        >
          {{ description }}
        </DialogDescription>

        <div class="mt-4">
          <slot />
        </div>

        <div
          v-if="$slots.footer"
          class="mt-5 flex justify-end gap-2"
        >
          <slot name="footer" />
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
