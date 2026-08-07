<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import UserAvatar from '@/components/user/UserAvatar.vue'
import type { Post } from '@/types/api'
import { formatDateTime, formatRelativeTime } from '@/utils/format/datetime'

/**
 * 一則發文。
 *
 * <p>純呈現元件：不打 API、不開對話框，只把使用者的意圖以事件送給父層。
 * 好處是同一張卡片能同時用在動態牆與詳情頁，且測試時不需要準備任何環境。
 */
const props = withDefaults(
  defineProps<{
    post: Post
    /** 是否顯示編輯與刪除（由父層依登入者是否為作者判斷）。 */
    canManage?: boolean
    /** 詳情頁已經在看這則發文了，不需要再給一個「查看留言」的連結。 */
    linkToDetail?: boolean
  }>(),
  { canManage: false, linkToDetail: true },
)

defineEmits<{ edit: [post: Post]; remove: [post: Post] }>()

const edited = computed(() => props.post.updatedAt !== props.post.createdAt)
</script>

<template>
  <n-card
    class="post-card"
    :bordered="false"
  >
    <header class="post-card__header">
      <UserAvatar
        :name="post.author.userName"
        :image="post.author.coverImage ?? null"
        :size="40"
      />
      <div class="post-card__meta">
        <span class="post-card__author">{{ post.author.userName }}</span>
        <n-tooltip trigger="hover">
          <template #trigger>
            <span class="post-card__time">
              {{ formatRelativeTime(post.createdAt) }}<template v-if="edited">（已編輯）</template>
            </span>
          </template>
          {{ formatDateTime(post.createdAt) }}
        </n-tooltip>
      </div>

      <n-space
        v-if="canManage"
        :size="4"
      >
        <n-button
          quaternary
          size="small"
          @click="$emit('edit', post)"
        >
          編輯
        </n-button>
        <n-button
          quaternary
          size="small"
          type="error"
          @click="$emit('remove', post)"
        >
          刪除
        </n-button>
      </n-space>
    </header>

    <p class="user-content post-card__content">
      {{ post.content }}
    </p>

    <n-image
      v-if="post.image"
      :src="post.image"
      :img-props="{ alt: `${post.author.userName} 的發文圖片` }"
      class="post-card__image"
      object-fit="cover"
    />

    <footer class="post-card__footer">
      <RouterLink
        v-if="linkToDetail"
        :to="{ name: 'post-detail', params: { postId: post.postId } }"
        class="post-card__comments"
      >
        留言 {{ post.commentCount }}
      </RouterLink>
      <span
        v-else
        class="post-card__comments"
      >留言 {{ post.commentCount }}</span>
    </footer>
  </n-card>
</template>

<style scoped>
.post-card {
  margin-bottom: 0.875rem;
}

.post-card__header {
  align-items: center;
  display: flex;
  gap: 0.75rem;
}

.post-card__meta {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.post-card__author {
  font-weight: 600;
}

.post-card__time {
  color: var(--n-text-color-3);
  cursor: default;
  font-size: 0.8125rem;
}

.post-card__content {
  margin: 0.75rem 0 0;
}

.post-card__image {
  border-radius: 8px;
  margin-top: 0.75rem;
  max-height: 24rem;
  overflow: hidden;
}

.post-card__footer {
  margin-top: 0.75rem;
}

.post-card__comments {
  color: var(--n-text-color-3);
  font-size: 0.875rem;
  text-decoration: none;
}

.post-card__comments:hover {
  text-decoration: underline;
}
</style>
