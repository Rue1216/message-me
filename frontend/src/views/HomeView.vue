<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'

import { ApiClientError } from '@/api/client/http'
import { createPost, deletePost, fetchPosts } from '@/api/resources/posts'
import PostCard from '@/components/post/PostCard.vue'
import PostEditorModal from '@/components/post/PostEditorModal.vue'
import PostForm from '@/components/post/PostForm.vue'
import { useAuthStore } from '@/stores/auth'
import type { Post, PostPayload } from '@/types/api'

/** 動態牆：所有人的發文，新到舊。 */

const PAGE_SIZE = 10

const auth = useAuthStore()
const message = useMessage()
const dialog = useDialog()

const posts = ref<Post[]>([])
const page = ref(1)
const totalElements = ref(0)
const loading = ref(false)
const loadError = ref<string | null>(null)

const composerRef = ref<InstanceType<typeof PostForm> | null>(null)
const publishing = ref(false)

const editingPost = ref<Post | null>(null)
const showEditor = ref(false)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const result = await fetchPosts(page.value, PAGE_SIZE)
    posts.value = result.items
    totalElements.value = result.totalElements
    // 刪掉最後一頁的最後一則後，原本的頁碼會落在資料範圍外，往前退一頁再取一次
    if (result.items.length === 0 && page.value > 1 && result.totalPages > 0) {
      page.value = Math.min(page.value - 1, result.totalPages)
      await load()
    }
  } catch (error) {
    loadError.value = error instanceof ApiClientError ? error.message : '載入失敗，請稍後再試'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function handlePageChange(next: number): void {
  page.value = next
  void load()
}

async function handlePublish(payload: PostPayload): Promise<void> {
  publishing.value = true
  try {
    await createPost(payload)
    composerRef.value?.reset()
    message.success('發文成功')
    page.value = 1
    await load()
  } catch (error) {
    message.error(error instanceof ApiClientError ? error.message : '發文失敗，請稍後再試')
  } finally {
    publishing.value = false
  }
}

function openEditor(post: Post): void {
  editingPost.value = post
  showEditor.value = true
}

/** 編輯成功後就地更新該則，不重新抓整頁——使用者的捲動位置因此不會跳動。 */
function handleUpdated(updated: Post): void {
  const index = posts.value.findIndex((item) => item.postId === updated.postId)
  if (index !== -1) {
    posts.value[index] = updated
  }
}

function confirmRemove(post: Post): void {
  dialog.warning({
    title: '刪除發文',
    content: '刪除後這則發文與它底下的所有留言都會一併消失，確定要刪除嗎？',
    positiveText: '刪除',
    negativeText: '取消',
    onPositiveClick: () => {
      void remove(post)
    },
  })
}

async function remove(post: Post): Promise<void> {
  try {
    await deletePost(post.postId)
    message.success('發文已刪除')
    await load()
  } catch (error) {
    message.error(error instanceof ApiClientError ? error.message : '刪除失敗，請稍後再試')
  }
}

function canManage(post: Post): boolean {
  return auth.currentUserId === post.author.userId
}
</script>

<template>
  <section>
    <n-card
      v-if="auth.isAuthenticated"
      class="composer"
      :bordered="false"
    >
      <PostForm
        ref="composerRef"
        submit-label="發布"
        :submitting="publishing"
        @submit="handlePublish"
      />
    </n-card>

    <n-alert
      v-else
      type="default"
      class="composer"
    >
      <RouterLink :to="{ name: 'login' }">
        登入
      </RouterLink>
      後即可發文與留言。
    </n-alert>

    <n-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      class="composer"
    >
      <n-button
        size="small"
        @click="load"
      >
        重新載入
      </n-button>
    </n-alert>

    <n-spin :show="loading">
      <n-empty
        v-if="!loading && posts.length === 0"
        description="還沒有任何發文"
        class="empty"
      />

      <PostCard
        v-for="post in posts"
        :key="post.postId"
        :post="post"
        :can-manage="canManage(post)"
        @edit="openEditor"
        @remove="confirmRemove"
      />
    </n-spin>

    <div
      v-if="totalElements > PAGE_SIZE"
      class="pagination"
    >
      <n-pagination
        :page="page"
        :page-size="PAGE_SIZE"
        :item-count="totalElements"
        @update:page="handlePageChange"
      />
    </div>

    <PostEditorModal
      v-model:show="showEditor"
      :post="editingPost"
      @updated="handleUpdated"
    />
  </section>
</template>

<style scoped>
.composer {
  margin-bottom: 0.875rem;
}

.empty {
  padding: 2.5rem 0;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 1rem;
}
</style>
