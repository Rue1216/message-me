<script setup lang="ts">
import { computed, ref } from 'vue'

import AppButton from '@/components/ui/AppButton.vue'
import AppTextarea from '@/components/ui/AppTextarea.vue'
import { COMMENT_CONTENT_MAX_LENGTH, validateCommentContent } from '@/utils/validation/post'

/** 新增或編輯留言的輸入框。與 PostForm 一樣只負責蒐集內容，API 交給頁面處理。 */
const props = withDefaults(
  defineProps<{
    submitting?: boolean
    initialContent?: string
    submitLabel?: string
    placeholder?: string
    /** 編輯模式下顯示取消鍵。 */
    cancellable?: boolean
  }>(),
  {
    submitting: false,
    initialContent: '',
    submitLabel: '送出留言',
    placeholder: '留下你的想法…',
    cancellable: false,
  },
)

const emit = defineEmits<{ submit: [content: string]; cancel: [] }>()

const content = ref(props.initialContent)
const errorMessage = ref<string | null>(null)

const remaining = computed(() => COMMENT_CONTENT_MAX_LENGTH - content.value.length)

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
  <div class="flex flex-col gap-2">
    <label
      for="comment-content"
      class="sr-only"
    >留言內容</label>
    <AppTextarea
      id="comment-content"
      v-model="content"
      :placeholder="placeholder"
      :invalid="Boolean(errorMessage)"
      :min-rows="2"
      :max-rows="6"
      :aria-describedby="errorMessage ? 'comment-error' : 'comment-hint'"
      @keydown.ctrl.enter="handleSubmit"
      @keydown.meta.enter="handleSubmit"
    />
    <p
      v-if="errorMessage"
      id="comment-error"
      class="text-sm text-destructive"
      role="alert"
    >
      {{ errorMessage }}
    </p>
    <p
      v-else
      id="comment-hint"
      class="text-right text-xs"
      :class="remaining < 0 ? 'text-destructive' : 'text-muted-foreground'"
    >
      還可以輸入 {{ remaining }} 字
    </p>

    <div class="flex justify-end gap-2">
      <AppButton
        v-if="cancellable"
        variant="ghost"
        size="sm"
        @click="emit('cancel')"
      >
        取消
      </AppButton>
      <AppButton
        size="sm"
        :loading="submitting"
        @click="handleSubmit"
      >
        {{ submitLabel }}
      </AppButton>
    </div>
  </div>
</template>
