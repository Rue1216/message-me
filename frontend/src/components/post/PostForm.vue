<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import ImageUploader from '@/components/common/ImageUploader.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppTextarea from '@/components/ui/AppTextarea.vue'
import type { PostPayload } from '@/types/api'
import { POST_CONTENT_MAX_LENGTH, validatePostContent } from '@/utils/validation/post'

/**
 * 發文的輸入表單，新增與編輯共用。
 *
 * <p>只負責蒐集內容與圖片並回報給父層；要打哪一支 API、成功後畫面怎麼變，
 * 由使用它的頁面決定。這讓同一份表單能同時服務動態牆的發文框與編輯對話框。
 *
 * <p><strong>草稿保存</strong>：打到一半按到返回或不小心關掉分頁，內容不會消失。
 * 存在 sessionStorage 而非 localStorage——草稿的生命週期就該與這個分頁一致，
 * 也與權杖的保存策略維持一致。編輯既有發文時不啟用，
 * 否則使用者的草稿會蓋掉他實際想改的那篇。
 */
const props = withDefaults(
  defineProps<{
    initialContent?: string
    initialImage?: string | null
    submitLabel?: string
    submitting?: boolean
    placeholder?: string
    /** 草稿的識別；不給就不保存草稿（編輯既有發文時的情形）。 */
    draftKey?: string | null
  }>(),
  {
    initialContent: '',
    initialImage: null,
    submitLabel: '送出',
    submitting: false,
    placeholder: '分享你的想法…　輸入 #標籤 可以被搜尋到',
    draftKey: null,
  },
)

const emit = defineEmits<{ submit: [payload: PostPayload] }>()

const content = ref(props.initialContent)
const image = ref<string | null>(props.initialImage)
const errorMessage = ref<string | null>(null)

const remaining = computed(() => POST_CONTENT_MAX_LENGTH - content.value.length)

function readDraft(): void {
  if (!props.draftKey) {
    return
  }
  try {
    const saved = sessionStorage.getItem(props.draftKey)
    if (saved) {
      content.value = saved
    }
  } catch {
    // 隱私設定可能禁止存取；草稿只是便利功能，失敗不影響發文
  }
}

watch(content, (value) => {
  if (!props.draftKey) {
    return
  }
  try {
    if (value.trim()) {
      sessionStorage.setItem(props.draftKey, value)
    } else {
      sessionStorage.removeItem(props.draftKey)
    }
  } catch {
    // 同上
  }
})

onMounted(readDraft)

function handleSubmit(): void {
  const problem = validatePostContent(content.value)
  errorMessage.value = problem
  if (problem) {
    return
  }
  emit('submit', { content: content.value.trim(), image: image.value })
}

function reset(): void {
  content.value = ''
  image.value = null
  errorMessage.value = null
  if (props.draftKey) {
    try {
      sessionStorage.removeItem(props.draftKey)
    } catch {
      // 同上
    }
  }
}

// 由父層在送出成功後呼叫；失敗時不清空，使用者才不必重打一次內容
defineExpose({ reset })
</script>

<template>
  <div class="flex flex-col gap-3">
    <div>
      <label
        for="post-content"
        class="sr-only"
      >發文內容</label>
      <!--
        Ctrl / Cmd + Enter 送出：長文輸入時，讓手不必離開鍵盤去找按鈕。
        單獨的 Enter 保留為換行——發文是多行內容，不該按一下就送出。
      -->
      <AppTextarea
        id="post-content"
        v-model="content"
        :placeholder="placeholder"
        :invalid="Boolean(errorMessage)"
        :aria-describedby="errorMessage ? 'post-content-error' : 'post-content-hint'"
        @keydown.ctrl.enter="handleSubmit"
        @keydown.meta.enter="handleSubmit"
      />
      <p
        v-if="errorMessage"
        id="post-content-error"
        class="mt-1 text-sm text-destructive"
        role="alert"
      >
        {{ errorMessage }}
      </p>
      <p
        v-else
        id="post-content-hint"
        class="mt-1 text-right text-xs"
        :class="remaining < 0 ? 'text-destructive' : 'text-muted-foreground'"
      >
        還可以輸入 {{ remaining }} 字
      </p>
    </div>

    <ImageUploader v-model="image" />

    <div class="flex justify-end">
      <AppButton
        :loading="submitting"
        @click="handleSubmit"
      >
        {{ submitLabel }}
      </AppButton>
    </div>
  </div>
</template>
