<script setup lang="ts">
import { MessageSquare, Pencil, Trash2 } from '@lucide/vue'
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import LikeButton from '@/components/post/LikeButton.vue'
import TagChip from '@/components/tag/TagChip.vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppDropdownMenu from '@/components/ui/AppDropdownMenu.vue'
import AppDropdownMenuItem from '@/components/ui/AppDropdownMenuItem.vue'
import type { Post } from '@/types/api'
import { formatDateTime, formatRelativeTime } from '@/utils/format/datetime'

/**
 * 一則發文。
 *
 * <p>純呈現元件：不打 API、不開對話框，只把使用者的意圖以事件送給父層。
 * 好處是同一張卡片能同時用在動態牆、詳情頁、搜尋與標籤頁，且測試時不需要準備任何環境。
 *
 * <p>唯一的例外是按讚——它被抽成 LikeButton 自行處理樂觀更新。
 * 讓每個使用這張卡片的頁面各自接一次按讚邏輯，只會產生四份一樣的程式碼。
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
  <AppCard
    as="article"
    class="mb-3 p-4"
  >
    <header class="flex items-center gap-3">
      <!-- 已刪除的帳號沒有個人頁可去，因此不做成連結 -->
      <RouterLink
        v-if="!post.author.deleted"
        :to="{ name: 'user-profile', params: { userId: post.author.userId } }"
        class="flex min-w-0 items-center gap-3"
      >
        <UserAvatar
          :name="post.author.userName"
          :image="post.author.coverImage ?? null"
        />
        <div class="flex min-w-0 flex-col">
          <span class="truncate font-semibold hover:underline">{{ post.author.userName }}</span>
          <time
            class="text-xs text-muted-foreground"
            :datetime="post.createdAt"
            :title="formatDateTime(post.createdAt)"
          >
            {{ formatRelativeTime(post.createdAt) }}<template v-if="edited">（已編輯）</template>
          </time>
        </div>
      </RouterLink>

      <div
        v-else
        class="flex min-w-0 items-center gap-3"
      >
        <UserAvatar :name="post.author.userName" />
        <div class="flex min-w-0 flex-col">
          <span class="truncate font-semibold text-muted-foreground">{{ post.author.userName }}</span>
          <time
            class="text-xs text-muted-foreground"
            :datetime="post.createdAt"
            :title="formatDateTime(post.createdAt)"
          >
            {{ formatRelativeTime(post.createdAt) }}
          </time>
        </div>
      </div>

      <AppDropdownMenu
        v-if="canManage"
        label="發文操作"
        class="ml-auto"
      >
        <AppDropdownMenuItem @select="$emit('edit', post)">
          <Pencil
            class="size-4"
            aria-hidden="true"
          />
          編輯
        </AppDropdownMenuItem>
        <AppDropdownMenuItem
          variant="destructive"
          @select="$emit('remove', post)"
        >
          <Trash2
            class="size-4"
            aria-hidden="true"
          />
          刪除
        </AppDropdownMenuItem>
      </AppDropdownMenu>
    </header>

    <p class="user-content mt-3">
      {{ post.content }}
    </p>

    <ul
      v-if="post.tags.length"
      class="mt-3 flex flex-wrap gap-1.5"
    >
      <li
        v-for="tag in post.tags"
        :key="tag"
      >
        <TagChip :name="tag" />
      </li>
    </ul>

    <!--
      固定 16:9 的容器搭配 object-cover：圖片載入前後版面高度不變，
      不會把下方的內容往下推（累積版面位移，CLS）。
    -->
    <div
      v-if="post.image"
      class="mt-3 aspect-video overflow-hidden rounded-lg bg-muted"
    >
      <img
        :src="post.image"
        :alt="`${post.author.userName} 的發文圖片`"
        loading="lazy"
        decoding="async"
        class="size-full object-cover"
      >
    </div>

    <footer class="mt-3 flex items-center gap-1">
      <LikeButton :post="post" />

      <RouterLink
        v-if="linkToDetail"
        :to="{ name: 'post-detail', params: { postId: post.postId } }"
        class="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-sm text-muted-foreground transition-colors hover:bg-muted"
      >
        <MessageSquare
          class="size-4"
          aria-hidden="true"
        />
        留言 {{ post.commentCount }}
      </RouterLink>
      <span
        v-else
        class="inline-flex items-center gap-1.5 px-2 py-1 text-sm text-muted-foreground"
      >
        <MessageSquare
          class="size-4"
          aria-hidden="true"
        />
        留言 {{ post.commentCount }}
      </span>
    </footer>
  </AppCard>
</template>
