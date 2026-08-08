<script setup lang="ts">
import AppCard from '@/components/ui/AppCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'

/**
 * 發文卡片的載入佔位。
 *
 * <p>形狀刻意對齊 PostCard：頭像、兩行標題、內容、動作列。
 * 骨架與真實內容的輪廓一致時，資料到達的瞬間不會有跳動感；
 * 若只放一個轉圈圖示，內容出現的那一刻整個版面都會重排。
 */
withDefaults(defineProps<{ count?: number }>(), { count: 3 })
</script>

<template>
  <div aria-busy="true">
    <!-- 對螢幕閱讀器公告載入狀態；視覺上的骨架本身是 aria-hidden 的 -->
    <span class="sr-only">載入中…</span>

    <AppCard
      v-for="n in count"
      :key="n"
      class="mb-3 p-4"
    >
      <div class="flex items-center gap-3">
        <AppSkeleton class="size-10 rounded-full" />
        <div class="flex flex-col gap-1.5">
          <AppSkeleton class="h-4 w-24" />
          <AppSkeleton class="h-3 w-16" />
        </div>
      </div>
      <div class="mt-4 flex flex-col gap-2">
        <AppSkeleton class="h-4 w-full" />
        <AppSkeleton class="h-4 w-4/5" />
      </div>
      <div class="mt-4 flex gap-3">
        <AppSkeleton class="h-6 w-14" />
        <AppSkeleton class="h-6 w-20" />
      </div>
    </AppCard>
  </div>
</template>
