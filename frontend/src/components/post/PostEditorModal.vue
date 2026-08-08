<script setup lang="ts">
import { ApiClientError } from '@/api/client/http'
import PostForm from '@/components/post/PostForm.vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import { usePostMutations } from '@/composables/usePostMutations'
import { useToast } from '@/composables/useToast'
import type { Post, PostPayload } from '@/types/api'

/**
 * 編輯發文的對話框。
 *
 * <p>後端的 PUT 為全欄位取代語意：不帶 image 就等於把圖片移除，表單因此把原本的圖片
 * 一併帶進來當初始值，使用者沒動它就會原樣送回去。
 *
 * <p>更新走 usePostMutations，成功後由它負責讓相關查詢失效——
 * 因此不需要再向父層回報結果，父層也不必自己更新列表。
 */
const props = defineProps<{ post: Post | null }>()

const open = defineModel<boolean>('open', { required: true })

const toast = useToast()
const { update } = usePostMutations()

function handleSubmit(payload: PostPayload): void {
  if (!props.post) {
    return
  }
  update.mutate(
    { postId: props.post.postId, payload },
    {
      onSuccess: () => {
        toast.success('發文已更新')
        open.value = false
      },
      onError: (error) => {
        toast.error(error instanceof ApiClientError ? error.message : '更新失敗，請稍後再試')
      },
    },
  )
}
</script>

<template>
  <AppDialog
    v-model:open="open"
    title="編輯發文"
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
      :initial-tags="post.tags"
      submit-label="儲存"
      :submitting="update.isPending.value"
      @submit="handleSubmit"
    />
  </AppDialog>
</template>
