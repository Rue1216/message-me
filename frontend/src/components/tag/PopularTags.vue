<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { useId } from 'vue'

import { fetchPopularTags } from '@/api/resources/tags'
import TagChip from '@/components/tag/TagChip.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import { tagKeys } from '@/queries/queryKeys'

/**
 * 熱門標籤。
 *
 * <p>資料變動不頻繁（標籤的使用次數只在發文增刪時改變），
 * 因此 staleTime 拉長到 5 分鐘，避免每次切換頁面都重新請求。
 * 同一頁出現多個實例時（桌機側欄 + 搜尋頁內），共用的 query 快取讓它們只請求一次。
 *
 * <p>標題的 id 由 `useId()` 產生而非寫死：多個實例同時存在於 DOM 中時，
 * 寫死的 id 會重複，`aria-labelledby` 也就指不到正確的那一個。
 */
const headingId = useId()

const { data: tags, isPending } = useQuery({
  queryKey: tagKeys.popular(),
  queryFn: () => fetchPopularTags(12),
  staleTime: 5 * 60_000,
})
</script>

<template>
  <AppCard
    as="section"
    class="sticky top-20 p-4"
    :aria-labelledby="headingId"
  >
    <h2
      :id="headingId"
      class="mb-3 text-sm font-semibold"
    >
      熱門標籤
    </h2>

    <div
      v-if="isPending"
      class="flex flex-wrap gap-2"
      aria-busy="true"
    >
      <AppSkeleton
        v-for="n in 6"
        :key="n"
        class="h-6 w-16 rounded-full"
      />
    </div>

    <p
      v-else-if="!tags?.length"
      class="text-sm text-muted-foreground"
    >
      還沒有人使用標籤。發文時加上標籤就會出現在這裡。
    </p>

    <ul
      v-else
      class="flex flex-wrap gap-2"
    >
      <li
        v-for="tag in tags"
        :key="tag.name"
      >
        <TagChip :name="tag.name" />
      </li>
    </ul>
  </AppCard>
</template>
