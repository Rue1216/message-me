<script setup lang="ts">
import { ref } from 'vue'

import ImageUploader from '@/components/ImageUploader.vue'
import type { PostPayload } from '@/types/api'
import { POST_CONTENT_MAX_LENGTH, validatePostContent } from '@/utils/validation'

/**
 * 發文的輸入表單，新增與編輯共用。
 *
 * <p>只負責蒐集內容與圖片並回報給父層；要打哪一支 API、成功後畫面怎麼變，
 * 由使用它的頁面決定。這讓同一份表單能同時服務動態牆的發文框與編輯對話框。
 */
const props = withDefaults(
  defineProps<{
    initialContent?: string
    initialImage?: string | null
    submitLabel?: string
    submitting?: boolean
    placeholder?: string
  }>(),
  {
    initialContent: '',
    initialImage: null,
    submitLabel: '送出',
    submitting: false,
    placeholder: '分享你的想法…',
  },
)

const emit = defineEmits<{ submit: [payload: PostPayload] }>()

const content = ref(props.initialContent)
const image = ref<string | null>(props.initialImage)
const errorMessage = ref<string | null>(null)

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
}

// 由父層在送出成功後呼叫；失敗時不清空，使用者才不必重打一次內容
defineExpose({ reset })
</script>

<template>
  <div class="post-form">
    <n-input
      v-model:value="content"
      type="textarea"
      :placeholder="placeholder"
      :autosize="{ minRows: 3, maxRows: 10 }"
      :maxlength="POST_CONTENT_MAX_LENGTH"
      show-count
      :status="errorMessage ? 'error' : undefined"
    />
    <p
      v-if="errorMessage"
      class="post-form__error"
    >
      {{ errorMessage }}
    </p>

    <ImageUploader v-model="image" />

    <div class="post-form__actions">
      <n-button
        type="primary"
        :loading="submitting"
        @click="handleSubmit"
      >
        {{ submitLabel }}
      </n-button>
    </div>
  </div>
</template>

<style scoped>
.post-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.post-form__error {
  color: var(--n-error-color, #d03050);
  font-size: 0.8125rem;
  margin: -0.5rem 0 0;
}

.post-form__actions {
  display: flex;
  justify-content: flex-end;
}
</style>
