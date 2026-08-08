<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'

import { ApiClientError } from '@/api/client/http'
import PostFeed from '@/components/post/PostFeed.vue'
import PostForm from '@/components/post/PostForm.vue'
import AppAlert from '@/components/ui/AppAlert.vue'
import AppCard from '@/components/ui/AppCard.vue'
import { usePostFeed } from '@/composables/usePostFeed'
import { usePostMutations } from '@/composables/usePostMutations'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import type { PostPayload } from '@/types/api'

/**
 * 動態牆：所有人的發文，新到舊，無限捲動。
 *
 * <p>與改版前相比，這個檔案少了 loading / error / page 三組 ref 與整套 try-catch——
 * 那些狀態現在由 TanStack Query 提供，列表與其編輯刪除則收在 PostFeed 裡。
 * 這裡只剩下「發文框」這件本頁獨有的事。
 */

const auth = useAuthStore()
const toast = useToast()

const feed = usePostFeed()
const { create } = usePostMutations()

const composerRef = ref<InstanceType<typeof PostForm> | null>(null)

function handlePublish(payload: PostPayload): void {
  create.mutate(payload, {
    onSuccess: () => {
      composerRef.value?.reset()
      toast.success('發文成功')
    },
    onError: (error) =>
      toast.error(error instanceof ApiClientError ? error.message : '發文失敗，請稍後再試'),
  })
}
</script>

<template>
  <section>
    <h1 class="sr-only">
      動態牆
    </h1>

    <AppCard
      v-if="auth.isAuthenticated"
      class="mb-4 p-4"
    >
      <PostForm
        ref="composerRef"
        submit-label="發布"
        :submitting="create.isPending.value"
        draft-key="message-me:draft:new-post"
        @submit="handlePublish"
      />
    </AppCard>

    <AppAlert
      v-else
      class="mb-4"
    >
      <RouterLink
        :to="{ name: 'login' }"
        class="font-medium text-primary hover:underline"
      >
        登入
      </RouterLink>
      後即可發文、留言與按讚。
    </AppAlert>

    <PostFeed
      :posts="feed.posts.value"
      :is-pending="feed.isPending.value"
      :is-fetching-next-page="feed.isFetchingNextPage.value"
      :has-next-page="feed.hasNextPage.value ?? false"
      :error="feed.error.value"
      empty-title="還沒有任何發文"
      empty-description="成為第一個分享想法的人吧。"
      :on-load-more="() => feed.fetchNextPage()"
      :on-retry="() => feed.refetch()"
    />
  </section>
</template>
