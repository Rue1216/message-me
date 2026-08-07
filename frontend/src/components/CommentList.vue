<script setup lang="ts">
import UserAvatar from '@/components/UserAvatar.vue'
import type { Comment } from '@/types/api'
import { formatDateTime, formatRelativeTime } from '@/utils/datetime'

/**
 * 留言列表。純呈現，刪除只送出事件由頁面處理。
 *
 * @param manageableUserId 目前登入者的 ID；只有自己的留言才顯示刪除
 */
withDefaults(
  defineProps<{
    comments: Comment[]
    manageableUserId?: number | null
  }>(),
  { manageableUserId: null },
)

defineEmits<{ remove: [comment: Comment] }>()
</script>

<template>
  <ul class="comment-list">
    <li
      v-for="comment in comments"
      :key="comment.commentId"
      class="comment"
    >
      <UserAvatar
        :name="comment.author.userName"
        :image="comment.author.coverImage ?? null"
        :size="32"
      />

      <div class="comment__body">
        <div class="comment__meta">
          <span class="comment__author">{{ comment.author.userName }}</span>
          <n-tooltip trigger="hover">
            <template #trigger>
              <span class="comment__time">{{ formatRelativeTime(comment.createdAt) }}</span>
            </template>
            {{ formatDateTime(comment.createdAt) }}
          </n-tooltip>
        </div>
        <p class="user-content comment__content">
          {{ comment.content }}
        </p>
      </div>

      <n-button
        v-if="manageableUserId !== null && manageableUserId === comment.author.userId"
        quaternary
        size="tiny"
        type="error"
        @click="$emit('remove', comment)"
      >
        刪除
      </n-button>
    </li>
  </ul>
</template>

<style scoped>
.comment-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  list-style: none;
  margin: 0;
  padding: 0;
}

.comment {
  align-items: flex-start;
  display: flex;
  gap: 0.75rem;
}

.comment__body {
  flex: 1;
  min-width: 0;
}

.comment__meta {
  align-items: baseline;
  display: flex;
  gap: 0.5rem;
}

.comment__author {
  font-size: 0.9375rem;
  font-weight: 600;
}

.comment__time {
  color: var(--n-text-color-3);
  cursor: default;
  font-size: 0.75rem;
}

.comment__content {
  margin: 0.125rem 0 0;
}
</style>
