<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'

import CommentForm from '@/components/comment/CommentForm.vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AppButton from '@/components/ui/AppButton.vue'
import type { Comment } from '@/types/api'
import { formatDateTime, formatRelativeTime } from '@/utils/format/datetime'

/**
 * 留言列表。
 *
 * <p>編輯狀態（目前正在改哪一則）是純粹的畫面狀態，因此留在這裡；
 * 實際的送出與刪除仍以事件交給頁面，維持這個元件不打 API 的性質。
 *
 * @param manageableUserId 目前登入者的 ID；只有自己的留言才顯示編輯與刪除
 */
withDefaults(
  defineProps<{
    comments: Comment[]
    manageableUserId?: number | null
    /** 正在送出的留言 ID，用於停用該列的按鈕。 */
    submittingId?: number | null
  }>(),
  { manageableUserId: null, submittingId: null },
)

const emit = defineEmits<{
  remove: [comment: Comment]
  update: [payload: { comment: Comment; content: string }]
}>()

const editingId = ref<number | null>(null)

function submitEdit(comment: Comment, content: string): void {
  emit('update', { comment, content })
  editingId.value = null
}
</script>

<template>
  <ul class="flex flex-col gap-4">
    <li
      v-for="comment in comments"
      :key="comment.commentId"
      class="flex items-start gap-3"
    >
      <UserAvatar
        :name="comment.author.userName"
        :image="comment.author.coverImage ?? null"
        size="sm"
      />

      <div class="min-w-0 flex-1">
        <div class="flex items-baseline gap-2">
          <RouterLink
            v-if="!comment.author.deleted"
            :to="{ name: 'user-profile', params: { userId: comment.author.userId } }"
            class="text-sm font-semibold hover:underline"
          >
            {{ comment.author.userName }}
          </RouterLink>
          <span
            v-else
            class="text-sm font-semibold text-muted-foreground"
          >{{ comment.author.userName }}</span>

          <time
            class="text-xs text-muted-foreground"
            :datetime="comment.createdAt"
            :title="formatDateTime(comment.createdAt)"
          >
            {{ formatRelativeTime(comment.createdAt) }}<template
              v-if="comment.updatedAt !== comment.createdAt"
            >（已編輯）</template>
          </time>
        </div>

        <CommentForm
          v-if="editingId === comment.commentId"
          class="mt-2"
          :initial-content="comment.content"
          submit-label="儲存"
          cancellable
          :submitting="submittingId === comment.commentId"
          @submit="(content) => submitEdit(comment, content)"
          @cancel="editingId = null"
        />
        <p
          v-else
          class="user-content mt-0.5 text-sm"
        >
          {{ comment.content }}
        </p>
      </div>

      <div
        v-if="
          manageableUserId !== null &&
            manageableUserId === comment.author.userId &&
            editingId !== comment.commentId
        "
        class="flex shrink-0 gap-0.5"
      >
        <AppButton
          variant="ghost"
          size="sm"
          class="h-7 px-2 text-xs"
          @click="editingId = comment.commentId"
        >
          編輯
        </AppButton>
        <AppButton
          variant="ghost"
          size="sm"
          class="h-7 px-2 text-xs text-destructive hover:bg-destructive/10"
          @click="$emit('remove', comment)"
        >
          刪除
        </AppButton>
      </div>
    </li>
  </ul>
</template>
