<script setup lang="ts">
import { Heart } from '@lucide/vue'
import { useRouter } from 'vue-router'

import { useLikePost } from '@/composables/useLikePost'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import { cn } from '@/lib/utils'
import type { Post } from '@/types/api'

/**
 * 按讚。
 *
 * <p>畫面在按下的當下就更新（樂觀更新，見 useLikePost），失敗才回滾。
 * 這是全站互動最頻繁的按鈕，等待一個來回會讓整個介面顯得遲鈍。
 *
 * <p>未登入者不隱藏這個按鈕：看得到但按下去會被導向登入頁，
 * 比一個「什麼都沒有」的位置更容易理解為什麼要註冊。
 */
const props = defineProps<{ post: Post }>()

const auth = useAuthStore()
const router = useRouter()
const toast = useToast()
const { mutate } = useLikePost()

function handleClick(): void {
  if (!auth.isAuthenticated) {
    toast.error('請先登入才能按讚')
    void router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  mutate({ postId: props.post.postId, liked: props.post.likedByMe })
}
</script>

<template>
  <button
    type="button"
    :class="
      cn(
        'inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-sm transition-colors hover:bg-muted',
        post.likedByMe ? 'text-destructive' : 'text-muted-foreground',
      )
    "
    :aria-pressed="post.likedByMe"
    :aria-label="post.likedByMe ? `取消對這篇發文的讚，目前 ${post.likeCount} 個讚` : `對這篇發文按讚，目前 ${post.likeCount} 個讚`"
    @click="handleClick"
  >
    <Heart
      class="size-4"
      :fill="post.likedByMe ? 'currentColor' : 'none'"
      aria-hidden="true"
    />
    <!-- 計數對螢幕閱讀器隱藏：它已經包含在上面的 aria-label 裡，重複朗讀只是噪音 -->
    <span aria-hidden="true">{{ post.likeCount }}</span>
  </button>
</template>
