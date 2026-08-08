<script setup lang="ts">
import { ref, useTemplateRef } from 'vue'

import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import PostCard from '@/components/post/PostCard.vue'
import PostEditorModal from '@/components/post/PostEditorModal.vue'
import PostSkeleton from '@/components/post/PostSkeleton.vue'
import AppButton from '@/components/ui/AppButton.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import { ApiClientError } from '@/api/client/http'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { usePostMutations } from '@/composables/usePostMutations'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import type { Post } from '@/types/api'

/**
 * 無限捲動的發文列表。
 *
 * <p>動態牆、搜尋結果與標籤頁三者的呈現完全相同，差別只在資料從哪個查詢來。
 * 把列表本身連同編輯、刪除、載入更多一併收在這裡，三個頁面就只需要傳入查詢結果。
 *
 * <p>編輯與刪除的實際行為由 usePostMutations 負責，其中刪除採樂觀移除——
 * 這是「刪除後捲動位置不再跳動」的來源。
 */
const props = defineProps<{
  posts: Post[]
  isPending: boolean
  isFetchingNextPage: boolean
  hasNextPage: boolean
  error: unknown
  emptyTitle: string
  emptyDescription?: string
  onLoadMore: () => void
  onRetry: () => void
}>()

const auth = useAuthStore()
const toast = useToast()
const { remove } = usePostMutations()

const editingPost = ref<Post | null>(null)
const showEditor = ref(false)

const pendingRemoval = ref<Post | null>(null)
const showRemoveConfirm = ref(false)

const sentinel = useTemplateRef<HTMLElement>('sentinel')

useInfiniteScroll(sentinel, {
  hasMore: () => props.hasNextPage,
  isLoading: () => props.isFetchingNextPage,
  onLoadMore: () => props.onLoadMore(),
})

function canManage(post: Post): boolean {
  return auth.currentUserId === post.author.userId
}

function openEditor(post: Post): void {
  editingPost.value = post
  showEditor.value = true
}

function confirmRemove(post: Post): void {
  pendingRemoval.value = post
  showRemoveConfirm.value = true
}

function performRemove(): void {
  const post = pendingRemoval.value
  if (!post) {
    return
  }
  remove.mutate(post.postId, {
    onSuccess: () => toast.success('發文已刪除'),
    onError: (error) =>
      toast.error(error instanceof ApiClientError ? error.message : '刪除失敗，請稍後再試'),
  })
}
</script>

<template>
  <div>
    <ErrorState
      v-if="error"
      :error="error"
      class="mb-3"
      @retry="onRetry"
    />

    <PostSkeleton v-if="isPending" />

    <EmptyState
      v-else-if="!posts.length"
      :title="emptyTitle"
      :description="emptyDescription"
    >
      <template
        v-if="$slots.emptyAction"
        #action
      >
        <slot name="emptyAction" />
      </template>
    </EmptyState>

    <template v-else>
      <PostCard
        v-for="post in posts"
        :key="post.postId"
        :post="post"
        :can-manage="canManage(post)"
        @edit="openEditor"
        @remove="confirmRemove"
      />

      <!--
        哨兵：進入視窗（含 200px 提前量）時載入下一頁。
        高度為 1px 而非 0——完全沒有高度的元素不會被 IntersectionObserver 觀察到。
      -->
      <div
        ref="sentinel"
        class="h-px"
        aria-hidden="true"
      />

      <div
        v-if="isFetchingNextPage"
        class="py-4"
      >
        <PostSkeleton :count="1" />
      </div>

      <!--
        自動載入之外仍提供按鈕：IntersectionObserver 在極少數情境下不會觸發
        （例如列表短到哨兵一直在畫面內卻沒有捲動事件），有個可按的東西是必要的後路。
      -->
      <div
        v-else-if="hasNextPage"
        class="flex justify-center py-4"
      >
        <AppButton
          variant="outline"
          size="sm"
          @click="onLoadMore"
        >
          載入更多
        </AppButton>
      </div>

      <p
        v-else-if="posts.length > 5"
        class="py-6 text-center text-sm text-muted-foreground"
      >
        已經看到底了
      </p>
    </template>

    <PostEditorModal
      v-model:open="showEditor"
      :post="editingPost"
    />

    <ConfirmDialog
      v-model:open="showRemoveConfirm"
      title="刪除發文"
      description="刪除後這則發文與它底下的所有留言、按讚都會一併消失，且無法復原。"
      confirm-label="刪除"
      @confirm="performRemove"
    />
  </div>
</template>
