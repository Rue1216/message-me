<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { Heart, MessageSquare, PenLine } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchUserActivities } from '@/api/resources/users'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import { userKeys } from '@/queries/queryKeys'
import { formatDateTime, formatRelativeTime } from '@/utils/format/datetime'

/**
 * 個人頁的合併動態：發文與留言交錯，新到舊。
 *
 * <p>兩種活動在同一條時間軸上，因此需要在視覺上區分——
 * 發文顯示內容本身，留言則顯示「在誰的哪篇發文下說了什麼」。
 * 少了原文的脈絡，一則留言單獨看幾乎沒有意義。
 *
 * <p>採頁碼分頁而非無限捲動：資料來自兩張資料表的 UNION，後端以 offset 分頁
 * （複合游標過於脆弱，見 sp_user_activity_list 的註解）。
 */
const props = withDefaults(defineProps<{ userId: number; emptyTitle?: string }>(), {
  emptyTitle: '還沒有任何動態',
})

const PAGE_SIZE = 20

const page = ref(1)

// 切換到另一位使用者時回到第一頁，否則會停在對方不存在的頁碼上
watch(
  () => props.userId,
  () => {
    page.value = 1
  },
)

const query = useQuery({
  queryKey: computed(() => userKeys.activities(props.userId, page.value)),
  queryFn: () => fetchUserActivities(props.userId, page.value, PAGE_SIZE),
  placeholderData: (previous) => previous,
})

const totalPages = computed(() => query.data.value?.totalPages ?? 0)
</script>

<template>
  <section aria-labelledby="activity-heading">
    <h2
      id="activity-heading"
      class="mb-3 font-semibold"
    >
      動態
    </h2>

    <ErrorState
      v-if="query.error.value"
      :error="query.error.value"
      class="mb-3"
      @retry="query.refetch()"
    />

    <div
      v-else-if="query.isPending.value"
      class="flex flex-col gap-3"
      aria-busy="true"
    >
      <AppCard
        v-for="n in 3"
        :key="n"
        class="p-4"
      >
        <AppSkeleton class="h-3 w-32" />
        <AppSkeleton class="mt-3 h-4 w-full" />
        <AppSkeleton class="mt-2 h-4 w-2/3" />
      </AppCard>
    </div>

    <EmptyState
      v-else-if="!query.data.value?.items.length"
      :title="emptyTitle"
      description="發文與留言都會出現在這條時間軸上。"
    />

    <ul
      v-else
      class="flex flex-col gap-3"
    >
      <li
        v-for="activity in query.data.value.items"
        :key="`${activity.type}-${activity.activityId}`"
      >
        <AppCard class="p-4">
          <div class="flex items-center gap-2 text-xs text-muted-foreground">
            <PenLine
              v-if="activity.type === 'POST'"
              class="size-3.5"
              aria-hidden="true"
            />
            <MessageSquare
              v-else
              class="size-3.5"
              aria-hidden="true"
            />
            <span>{{ activity.type === 'POST' ? '發布了一則發文' : '留言了' }}</span>
            <time
              :datetime="activity.createdAt"
              :title="formatDateTime(activity.createdAt)"
            >
              {{ formatRelativeTime(activity.createdAt) }}
            </time>
          </div>

          <!-- 留言：先給出原文的脈絡，再顯示留言內容 -->
          <RouterLink
            v-if="activity.type === 'COMMENT'"
            :to="{ name: 'post-detail', params: { postId: activity.postId } }"
            class="mt-2 block rounded-md border-l-2 border-border bg-muted/50 px-3 py-2 text-xs text-muted-foreground hover:bg-muted"
          >
            <span class="font-medium">{{ activity.postAuthorName }}</span> 的發文：
            <span class="user-content">{{ activity.postExcerpt }}</span>
          </RouterLink>

          <RouterLink
            :to="{ name: 'post-detail', params: { postId: activity.postId } }"
            class="mt-2 block"
          >
            <p class="user-content text-sm">
              {{ activity.content }}
            </p>
          </RouterLink>

          <div
            v-if="activity.type === 'POST'"
            class="mt-3 flex items-center gap-4 text-xs text-muted-foreground"
          >
            <span class="inline-flex items-center gap-1">
              <Heart
                class="size-3.5"
                aria-hidden="true"
              />
              {{ activity.likeCount }}
            </span>
            <span class="inline-flex items-center gap-1">
              <MessageSquare
                class="size-3.5"
                aria-hidden="true"
              />
              {{ activity.commentCount }}
            </span>
          </div>
        </AppCard>
      </li>
    </ul>

    <nav
      v-if="totalPages > 1"
      class="mt-4 flex items-center justify-center gap-2"
      aria-label="動態分頁"
    >
      <AppButton
        variant="outline"
        size="sm"
        :disabled="page <= 1"
        @click="page -= 1"
      >
        上一頁
      </AppButton>
      <span class="text-sm text-muted-foreground">第 {{ page }} / {{ totalPages }} 頁</span>
      <AppButton
        variant="outline"
        size="sm"
        :disabled="page >= totalPages"
        @click="page += 1"
      >
        下一頁
      </AppButton>
    </nav>
  </section>
</template>
