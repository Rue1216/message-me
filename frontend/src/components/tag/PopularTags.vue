<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'

import { fetchPopularTags } from '@/api/resources/tags'
import TagChip from '@/components/tag/TagChip.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import { tagKeys } from '@/queries/queryKeys'

/**
 * 熱門標籤側欄。
 *
 * <p>資料變動不頻繁（標籤的使用次數只在發文增刪時改變），
 * 因此 staleTime 拉長到 5 分鐘，避免每次切換頁面都重新請求。
 */
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
    aria-labelledby="popular-tags-heading"
  >
    <h2
      id="popular-tags-heading"
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
      還沒有人使用標籤。在發文中輸入 #標籤 就會出現在這裡。
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
