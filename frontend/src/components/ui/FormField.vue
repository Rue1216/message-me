<script setup lang="ts">
/**
 * 表單欄位的外框：標籤、輸入元件、錯誤訊息。
 *
 * <p>把 label 與錯誤訊息的關聯集中在這裡，是為了讓每個欄位都必然具備：
 * `for` / `id` 的配對（點標籤能聚焦到輸入框），以及 `aria-describedby` 指向錯誤訊息
 * （螢幕閱讀器會在讀出欄位名稱後接著讀出哪裡錯了）。
 * 這些細節如果交由每個表單自己寫，遲早會有地方漏掉。
 */
defineProps<{
  id: string
  label: string
  error?: string | null
  hint?: string
  /** 選填欄位在標籤上標示，讓使用者不必逐一嘗試才知道哪些可以留空。 */
  optional?: boolean
}>()
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <label
      :for="id"
      class="text-sm font-medium"
    >
      {{ label }}
      <span
        v-if="optional"
        class="font-normal text-muted-foreground"
      >（選填）</span>
    </label>

    <!-- 插槽接收 aria 屬性，由使用端綁到實際的輸入元件上 -->
    <slot
      :described-by="error ? `${id}-error` : hint ? `${id}-hint` : undefined"
      :invalid="Boolean(error)"
    />

    <p
      v-if="error"
      :id="`${id}-error`"
      class="text-sm text-destructive"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-else-if="hint"
      :id="`${id}-hint`"
      class="text-xs text-muted-foreground"
    >
      {{ hint }}
    </p>
  </div>
</template>
