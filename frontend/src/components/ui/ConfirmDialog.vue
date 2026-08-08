<script setup lang="ts">
import {
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogOverlay,
  AlertDialogPortal,
  AlertDialogRoot,
  AlertDialogTitle,
} from 'reka-ui'

/**
 * 破壞性操作的確認對話框。
 *
 * <p>用 AlertDialog 而不是一般的 Dialog：兩者在無障礙語意上並不相同。
 * AlertDialog 會把焦點預設落在取消鍵、不允許點擊背景關閉，
 * 並以 role="alertdialog" 讓螢幕閱讀器立即朗讀內容——
 * 對「刪除後無法復原」這類訊息而言，這些差異是必要的。
 */
withDefaults(
  defineProps<{
    title: string
    description?: string
    confirmLabel?: string
    cancelLabel?: string
    destructive?: boolean
  }>(),
  { confirmLabel: '確定', cancelLabel: '取消', destructive: true },
)

const emit = defineEmits<{ confirm: [] }>()

const open = defineModel<boolean>('open', { required: true })
</script>

<template>
  <AlertDialogRoot v-model:open="open">
    <AlertDialogPortal>
      <AlertDialogOverlay class="fixed inset-0 z-50 bg-black/50" />
      <AlertDialogContent
        class="fixed left-1/2 top-1/2 z-50 w-[calc(100vw-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-lg border border-border bg-card p-5 shadow-lg focus:outline-none"
      >
        <AlertDialogTitle class="text-base font-semibold">
          {{ title }}
        </AlertDialogTitle>
        <AlertDialogDescription
          v-if="description"
          class="mt-2 text-sm text-muted-foreground"
        >
          {{ description }}
        </AlertDialogDescription>

        <div class="mt-5 flex justify-end gap-2">
          <AlertDialogCancel
            class="inline-flex h-10 items-center rounded-md border border-border px-4 text-sm font-medium hover:bg-muted"
          >
            {{ cancelLabel }}
          </AlertDialogCancel>
          <AlertDialogAction
            :class="[
              'inline-flex h-10 items-center rounded-md px-4 text-sm font-medium',
              destructive
                ? 'bg-destructive text-destructive-foreground hover:bg-destructive/90'
                : 'bg-primary text-primary-foreground hover:bg-primary/90',
            ]"
            @click="emit('confirm')"
          >
            {{ confirmLabel }}
          </AlertDialogAction>
        </div>
      </AlertDialogContent>
    </AlertDialogPortal>
  </AlertDialogRoot>
</template>
