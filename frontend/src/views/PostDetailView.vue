<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { ArrowLeft } from '@lucide/vue'
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { fetchComments } from '@/api/resources/comments'
import { ApiClientError } from '@/api/client/http'
import { fetchPost } from '@/api/resources/posts'
import CommentForm from '@/components/comment/CommentForm.vue'
import CommentList from '@/components/comment/CommentList.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import PostCard from '@/components/post/PostCard.vue'
import PostEditorModal from '@/components/post/PostEditorModal.vue'
import PostSkeleton from '@/components/post/PostSkeleton.vue'
import AppAlert from '@/components/ui/AppAlert.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import { useCommentMutations } from '@/composables/useCommentMutations'
import { usePostMutations } from '@/composables/usePostMutations'
import { useToast } from '@/composables/useToast'
import { commentKeys, postKeys } from '@/queries/queryKeys'
import { useAuthStore } from '@/stores/auth'
import type { Comment } from '@/types/api'

/** 單篇發文與其留言。發文本身公開可讀，留言需要登入才能發表。 */

const COMMENT_PAGE_SIZE = 20

// 路由以 props 傳入，型別是字串（來自網址）
const props = defineProps<{ postId: string }>()

const postId = computed(() => Number(props.postId))

const auth = useAuthStore()
const router = useRouter()
const toast = useToast()

const commentPage = ref(1)

const postQuery = useQuery({
  queryKey: computed(() => postKeys.detail(postId.value)),
  queryFn: () => fetchPost(postId.value),
})

const commentsQuery = useQuery({
  queryKey: computed(() => commentKeys.page(postId.value, commentPage.value)),
  queryFn: () => fetchComments(postId.value, commentPage.value, COMMENT_PAGE_SIZE),
  // 換頁時保留上一頁的內容，避免列表整個消失再出現造成的閃動
  placeholderData: (previous) => previous,
})

const { remove: removePost } = usePostMutations()
const comments = useCommentMutations(postId.value)

const commentFormRef = ref<InstanceType<typeof CommentForm> | null>(null)
const showEditor = ref(false)
const showRemovePostConfirm = ref(false)
const pendingCommentRemoval = ref<Comment | null>(null)
const showRemoveCommentConfirm = ref(false)
const editingCommentId = ref<number | null>(null)

const canManagePost = computed(
  () =>
    postQuery.data.value !== undefined && auth.currentUserId === postQuery.data.value.author.userId,
)

const totalPages = computed(() => commentsQuery.data.value?.totalPages ?? 0)

/**
 * 新增留言。
 *
 * <p>留言由舊到新排列，新的一則落在最後。只有在它確實讓總頁數增加時才帶使用者過去——
 * 改版前是無條件跳到最後一頁，即使使用者正在讀第一頁的討論也會被彈走。
 */
function handleCommentSubmit(content: string): void {
  comments.create.mutate(content, {
    onSuccess: () => {
      commentFormRef.value?.reset()
      toast.success('留言成功')
      const total = commentsQuery.data.value?.totalElements ?? 0
      const lastPage = Math.max(1, Math.ceil((total + 1) / COMMENT_PAGE_SIZE))
      if (lastPage > commentPage.value) {
        commentPage.value = lastPage
      }
    },
    onError: (error) =>
      toast.error(error instanceof ApiClientError ? error.message : '留言失敗，請稍後再試'),
  })
}

function handleCommentUpdate({ comment, content }: { comment: Comment; content: string }): void {
  editingCommentId.value = comment.commentId
  comments.update.mutate(
    { commentId: comment.commentId, content },
    {
      onSuccess: () => toast.success('留言已更新'),
      onError: (error) =>
        toast.error(error instanceof ApiClientError ? error.message : '更新失敗，請稍後再試'),
      onSettled: () => {
        editingCommentId.value = null
      },
    },
  )
}

function confirmRemoveComment(comment: Comment): void {
  pendingCommentRemoval.value = comment
  showRemoveCommentConfirm.value = true
}

function performRemoveComment(): void {
  const comment = pendingCommentRemoval.value
  if (!comment) {
    return
  }
  comments.remove.mutate(comment.commentId, {
    onSuccess: () => {
      toast.success('留言已刪除')
      // 刪掉整頁最後一則時退回前一頁，避免停在空白頁
      if (commentsQuery.data.value?.items.length === 1 && commentPage.value > 1) {
        commentPage.value -= 1
      }
    },
    onError: (error) =>
      toast.error(error instanceof ApiClientError ? error.message : '刪除失敗，請稍後再試'),
  })
}

function performRemovePost(): void {
  removePost.mutate(postId.value, {
    onSuccess: () => {
      toast.success('發文已刪除')
      void router.replace({ name: 'home' })
    },
    onError: (error) =>
      toast.error(error instanceof ApiClientError ? error.message : '刪除失敗，請稍後再試'),
  })
}
</script>

<template>
  <section>
    <AppButton
      variant="ghost"
      size="sm"
      class="mb-3"
      @click="router.back()"
    >
      <ArrowLeft
        class="size-4"
        aria-hidden="true"
      />
      返回
    </AppButton>

    <PostSkeleton
      v-if="postQuery.isPending.value"
      :count="1"
    />

    <div v-else-if="postQuery.error.value">
      <ErrorState
        :error="postQuery.error.value"
        fallback="看不到這則發文"
        @retry="postQuery.refetch()"
      />
      <div class="mt-4 flex justify-center">
        <RouterLink
          :to="{ name: 'home' }"
          class="text-sm text-primary hover:underline"
        >
          回動態牆
        </RouterLink>
      </div>
    </div>

    <template v-else-if="postQuery.data.value">
      <PostCard
        :post="postQuery.data.value"
        :can-manage="canManagePost"
        :link-to-detail="false"
        @edit="showEditor = true"
        @remove="showRemovePostConfirm = true"
      />

      <AppCard
        as="section"
        class="p-4"
        aria-labelledby="comments-heading"
      >
        <h2
          id="comments-heading"
          class="mb-3 font-semibold"
        >
          留言 {{ postQuery.data.value.commentCount }}
        </h2>

        <CommentForm
          v-if="auth.isAuthenticated"
          ref="commentFormRef"
          :submitting="comments.create.isPending.value"
          @submit="handleCommentSubmit"
        />
        <AppAlert v-else>
          <RouterLink
            :to="{ name: 'login' }"
            class="font-medium text-primary hover:underline"
          >
            登入
          </RouterLink>
          後即可留言。
        </AppAlert>

        <div class="my-4 h-px bg-border" />

        <div
          v-if="commentsQuery.isPending.value"
          class="flex flex-col gap-4"
          aria-busy="true"
        >
          <div
            v-for="n in 3"
            :key="n"
            class="flex gap-3"
          >
            <AppSkeleton class="size-7 rounded-full" />
            <div class="flex flex-1 flex-col gap-1.5">
              <AppSkeleton class="h-3 w-24" />
              <AppSkeleton class="h-4 w-3/4" />
            </div>
          </div>
        </div>

        <EmptyState
          v-else-if="!commentsQuery.data.value?.items.length"
          title="還沒有留言"
          description="來當第一個吧。"
        />

        <CommentList
          v-else
          :comments="commentsQuery.data.value.items"
          :manageable-user-id="auth.currentUserId"
          :submitting-id="editingCommentId"
          @remove="confirmRemoveComment"
          @update="handleCommentUpdate"
        />

        <nav
          v-if="totalPages > 1"
          class="mt-4 flex items-center justify-center gap-2"
          aria-label="留言分頁"
        >
          <AppButton
            variant="outline"
            size="sm"
            :disabled="commentPage <= 1"
            @click="commentPage -= 1"
          >
            上一頁
          </AppButton>
          <span class="text-sm text-muted-foreground">
            第 {{ commentPage }} / {{ totalPages }} 頁
          </span>
          <AppButton
            variant="outline"
            size="sm"
            :disabled="commentPage >= totalPages"
            @click="commentPage += 1"
          >
            下一頁
          </AppButton>
        </nav>
      </AppCard>
    </template>

    <PostEditorModal
      v-model:open="showEditor"
      :post="postQuery.data.value ?? null"
    />

    <ConfirmDialog
      v-model:open="showRemovePostConfirm"
      title="刪除發文"
      description="刪除後這則發文與它底下的所有留言、按讚都會一併消失，且無法復原。"
      confirm-label="刪除"
      @confirm="performRemovePost"
    />

    <ConfirmDialog
      v-model:open="showRemoveCommentConfirm"
      title="刪除留言"
      description="確定要刪除這則留言嗎？此操作無法復原。"
      confirm-label="刪除"
      @confirm="performRemoveComment"
    />
  </section>
</template>
