<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'

import { fetchPublicUser } from '@/api/resources/users'
import ErrorState from '@/components/common/ErrorState.vue'
import ActivityTimeline from '@/components/user/ActivityTimeline.vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AppAlert from '@/components/ui/AppAlert.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import { userKeys } from '@/queries/queryKeys'
import { formatDateTime } from '@/utils/format/datetime'

/**
 * 他人的公開個人檔案。
 *
 * <p>這個頁面補上了改版前的一個明顯缺口：後端早就有 `GET /api/users/{userId}`，
 * 但前端沒有任何地方連過去——點作者名字不會有反應。
 *
 * <p>已刪除的帳號仍然看得到（而非 404）：他的發文與留言還留在別人的討論串中，
 * 點進來應該得到一個「這個人已經離開了」的說明，而不是一個壞掉的連結。
 */

// 路由以 props 傳入，型別是字串（來自網址）
const props = defineProps<{ userId: string }>()

const userId = computed(() => Number(props.userId))

const query = useQuery({
  queryKey: computed(() => userKeys.detail(userId.value)),
  queryFn: () => fetchPublicUser(userId.value),
})
</script>

<template>
  <div>
    <ErrorState
      v-if="query.error.value"
      :error="query.error.value"
      fallback="找不到這位使用者"
      @retry="query.refetch()"
    />

    <AppCard
      v-else-if="query.isPending.value"
      class="mb-4 p-5"
    >
      <div class="flex items-center gap-4">
        <AppSkeleton class="size-20 rounded-full" />
        <div class="flex flex-col gap-2">
          <AppSkeleton class="h-5 w-32" />
          <AppSkeleton class="h-3 w-24" />
        </div>
      </div>
    </AppCard>

    <template v-else-if="query.data.value">
      <AppCard
        as="header"
        class="mb-4 p-5"
      >
        <div class="flex flex-col items-start gap-4 sm:flex-row sm:items-center">
          <UserAvatar
            :name="query.data.value.userName"
            :image="query.data.value.coverImage ?? null"
            size="xl"
          />
          <div class="min-w-0">
            <h1 class="text-xl font-bold">
              {{ query.data.value.userName }}
            </h1>
            <p class="mt-0.5 text-sm text-muted-foreground">
              加入於 {{ formatDateTime(query.data.value.createdAt) }}
            </p>
            <p
              v-if="query.data.value.biography"
              class="user-content mt-2 text-sm"
            >
              {{ query.data.value.biography }}
            </p>
          </div>
        </div>

        <AppAlert
          v-if="query.data.value.deleted"
          class="mt-4"
        >
          這個帳號已經刪除。過去的發文與留言仍保留在原本的討論串中。
        </AppAlert>
      </AppCard>

      <ActivityTimeline
        :user-id="userId"
        empty-title="這位使用者還沒有任何動態"
      />
    </template>
  </div>
</template>
