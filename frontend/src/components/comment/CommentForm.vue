<script setup lang="ts">
import { ref } from 'vue'

import { COMMENT_CONTENT_MAX_LENGTH, validateCommentContent } from '@/utils/validation/post'

/** 新增留言的輸入框。與 PostForm 一樣只負責蒐集內容，API 交給頁面處理。 */
withDefaults(defineProps<{ submitting?: boolean }>(), { submitting: false })

const emit = defineEmits<{ submit: [content: string] }>()

const content = ref('')
const errorMessage = ref<string | null>(null)

function handleSubmit(): void {
  const problem = validateCommentContent(content.value)
  errorMessage.value = problem
  if (problem) {
    return
  }
  emit('submit', content.value.trim())
}

function reset(): void {
  content.value = ''
  errorMessage.value = null
}

defineExpose({ reset })
</script>

<template>
  <div class="comment-form">
    <n-input
      v-model:value="content"
      type="textarea"
      placeholder="留下你的想法…"
      :autosize="{ minRows: 2, maxRows: 6 }"
      :maxlength="COMMENT_CONTENT_MAX_LENGTH"
      show-count
      :status="errorMessage ? 'error' : undefined"
    />
    <p
      v-if="errorMessage"
      class="comment-form__error"
    >
      {{ errorMessage }}
    </p>

    <div class="comment-form__actions">
      <n-button
        type="primary"
        size="small"
        :loading="submitting"
        @click="handleSubmit"
      >
        送出留言
      </n-button>
    </div>
  </div>
</template>

<style scoped>
.comment-form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.comment-form__error {
  color: var(--n-error-color, #d03050);
  font-size: 0.8125rem;
  margin: 0;
}

.comment-form__actions {
  display: flex;
  justify-content: flex-end;
}
</style>
