<script setup lang="ts">
import { ref } from 'vue'
import { useMessage } from 'naive-ui'

import { updatePost } from '@/api/resources/posts'
import { ApiClientError } from '@/api/client/http'
import PostForm from '@/components/post/PostForm.vue'
import type { Post, PostPayload } from '@/types/api'

/**
 * 編輯發文的對話框。
 *
 * 後端的 PUT 為全欄位取代語意：不帶 image 就等於把圖片移除，表單因此把原本的圖片
 * 一併帶進來當初始值，使用者沒動它就會原樣送回去。
 */
const props = defineProps<{ show: boolean; post: Post | null }>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  updated: [post: Post]
}>()

const message = useMessage()
const submitting = ref(false)

async function handleSubmit(payload: PostPayload): Promise<void> {
  if (!props.post) {
    return
  }
  submitting.value = true
  try {
    const updated = await updatePost(props.post.postId, payload)
    message.success('發文已更新')
    emit('updated', updated)
    emit('update:show', false)
  } catch (error) {
    message.error(error instanceof ApiClientError ? error.message : '更新失敗，請稍後再試')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <n-modal
    :show="show"
    preset="card"
    title="編輯發文"
    class="post-editor-modal"
    @update:show="emit('update:show', $event)"
  >
    <!--
      key 綁定發文 ID：換一則發文編輯時強制重建表單，
      否則 PostForm 內的 ref 會停留在上一則的內容。
    -->
    <PostForm
      v-if="post"
      :key="post.postId"
      :initial-content="post.content"
      :initial-image="post.image ?? null"
      submit-label="儲存"
      :submitting="submitting"
      @submit="handleSubmit"
    />
  </n-modal>
</template>

<style scoped>
.post-editor-modal {
  max-width: 34rem;
  width: 90vw;
}
</style>
