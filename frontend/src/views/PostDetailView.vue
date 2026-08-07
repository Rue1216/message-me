<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import { useRouter } from 'vue-router'

import { createComment, deleteComment, fetchComments } from '@/api/comments'
import { ApiClientError } from '@/api/http'
import { deletePost, fetchPost } from '@/api/posts'
import CommentForm from '@/components/CommentForm.vue'
import CommentList from '@/components/CommentList.vue'
import PostCard from '@/components/PostCard.vue'
import PostEditorModal from '@/components/PostEditorModal.vue'
import { useAuthStore } from '@/stores/auth'
import type { Comment, Post } from '@/types/api'

/** 單篇發文與其留言。發文本身公開可讀，留言需要登入才能發表。 */

const COMMENT_PAGE_SIZE = 20

// 路由以 props 傳入，型別是字串（來自網址）
const props = defineProps<{ postId: string }>()

const auth = useAuthStore()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const postId = computed(() => Number(props.postId))

const post = ref<Post | null>(null)
const loadError = ref<string | null>(null)
const loadingPost = ref(false)

const comments = ref<Comment[]>([])
const commentPage = ref(1)
const commentTotal = ref(0)
const loadingComments = ref(false)

const commentFormRef = ref<InstanceType<typeof CommentForm> | null>(null)
const submittingComment = ref(false)
const showEditor = ref(false)

const canManagePost = computed(
  () => post.value !== null && auth.currentUserId === post.value.author.userId,
)

async function loadPost(): Promise<void> {
  loadingPost.value = true
  loadError.value = null
  try {
    post.value = await fetchPost(postId.value)
  } catch (error) {
    loadError.value = error instanceof ApiClientError ? error.message : '載入失敗，請稍後再試'
  } finally {
    loadingPost.value = false
  }
}

async function loadComments(): Promise<void> {
  loadingComments.value = true
  try {
    const result = await fetchComments(postId.value, commentPage.value, COMMENT_PAGE_SIZE)
    comments.value = result.items
    commentTotal.value = result.totalElements
  } catch (error) {
    message.error(error instanceof ApiClientError ? error.message : '留言載入失敗')
  } finally {
    loadingComments.value = false
  }
}

onMounted(async () => {
  await loadPost()
  if (post.value) {
    await loadComments()
  }
})

function handleCommentPageChange(next: number): void {
  commentPage.value = next
  void loadComments()
}

async function handleCommentSubmit(content: string): Promise<void> {
  submittingComment.value = true
  try {
    await createComment(postId.value, content)
    commentFormRef.value?.reset()
    message.success('留言成功')
    // 留言由舊到新排序，新的一則落在最後一頁；連同發文的留言數一起重新取回
    const lastPage = Math.max(1, Math.ceil((commentTotal.value + 1) / COMMENT_PAGE_SIZE))
    commentPage.value = lastPage
    await Promise.all([loadPost(), loadComments()])
  } catch (error) {
    message.error(error instanceof ApiClientError ? error.message : '留言失敗，請稍後再試')
  } finally {
    submittingComment.value = false
  }
}

function confirmRemoveComment(comment: Comment): void {
  dialog.warning({
    title: '刪除留言',
    content: '確定要刪除這則留言嗎？',
    positiveText: '刪除',
    negativeText: '取消',
    onPositiveClick: () => {
      void removeComment(comment)
    },
  })
}

async function removeComment(comment: Comment): Promise<void> {
  try {
    await deleteComment(comment.commentId)
    message.success('留言已刪除')
    // 刪掉整頁最後一則時退回前一頁，避免停在空白頁
    if (comments.value.length === 1 && commentPage.value > 1) {
      commentPage.value -= 1
    }
    await Promise.all([loadPost(), loadComments()])
  } catch (error) {
    message.error(error instanceof ApiClientError ? error.message : '刪除失敗，請稍後再試')
  }
}

function confirmRemovePost(): void {
  dialog.warning({
    title: '刪除發文',
    content: '刪除後這則發文與它底下的所有留言都會一併消失，確定要刪除嗎？',
    positiveText: '刪除',
    negativeText: '取消',
    onPositiveClick: () => {
      void removePost()
    },
  })
}

async function removePost(): Promise<void> {
  try {
    await deletePost(postId.value)
    message.success('發文已刪除')
    await router.replace({ name: 'home' })
  } catch (error) {
    message.error(error instanceof ApiClientError ? error.message : '刪除失敗，請稍後再試')
  }
}

function handleUpdated(updated: Post): void {
  post.value = updated
}
</script>

<template>
  <section>
    <n-button
      quaternary
      size="small"
      class="back"
      @click="router.push({ name: 'home' })"
    >
      ← 回動態牆
    </n-button>

    <n-spin :show="loadingPost">
      <n-result
        v-if="loadError"
        status="404"
        title="看不到這則發文"
        :description="loadError"
        class="load-error"
      />

      <template v-else-if="post">
        <PostCard
          :post="post"
          :can-manage="canManagePost"
          :link-to-detail="false"
          @edit="showEditor = true"
          @remove="confirmRemovePost"
        />

        <n-card
          :bordered="false"
          title="留言"
        >
          <CommentForm
            v-if="auth.isAuthenticated"
            ref="commentFormRef"
            :submitting="submittingComment"
            @submit="handleCommentSubmit"
          />
          <n-alert
            v-else
            type="default"
          >
            <RouterLink :to="{ name: 'login' }">
              登入
            </RouterLink>
            後即可留言。
          </n-alert>

          <n-divider />

          <n-spin :show="loadingComments">
            <n-empty
              v-if="!loadingComments && comments.length === 0"
              description="還沒有留言，來當第一個吧"
            />
            <CommentList
              v-else
              :comments="comments"
              :manageable-user-id="auth.currentUserId"
              @remove="confirmRemoveComment"
            />
          </n-spin>

          <div
            v-if="commentTotal > COMMENT_PAGE_SIZE"
            class="pagination"
          >
            <n-pagination
              :page="commentPage"
              :page-size="COMMENT_PAGE_SIZE"
              :item-count="commentTotal"
              @update:page="handleCommentPageChange"
            />
          </div>
        </n-card>
      </template>
    </n-spin>

    <PostEditorModal
      v-model:show="showEditor"
      :post="post"
      @updated="handleUpdated"
    />
  </section>
</template>

<style scoped>
.back {
  margin-bottom: 0.75rem;
}

.load-error {
  padding: 2.5rem 0;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 1rem;
}
</style>
