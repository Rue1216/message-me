<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import PostFeed from '@/components/post/PostFeed.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { usePostSearch } from '@/composables/usePostFeed'

/**
 * 搜尋結果。
 *
 * <p>關鍵字取自網址而非元件內的狀態：搜尋結果應該是可以分享、可以加入書籤、
 * 可以用瀏覽器的上一頁回到的——這些都要求它存在於 URL 中。
 * 頁首的搜尋框只負責把使用者導到這個網址。
 */
const route = useRoute()

const keyword = computed(() => (typeof route.query.q === 'string' ? route.query.q : ''))

const search = usePostSearch(keyword)
</script>

<template>
  <section>
    <h1 class="mb-4 text-lg font-semibold">
      <template v-if="keyword">
        「{{ keyword }}」的搜尋結果
      </template>
      <template v-else>
        搜尋
      </template>
    </h1>

    <EmptyState
      v-if="!keyword"
      title="輸入關鍵字開始搜尋"
      description="可以搜尋發文內容。想找標籤的話，直接點選文章上的 #標籤 更快。"
    />

    <PostFeed
      v-else
      :posts="search.posts.value"
      :is-pending="search.isPending.value"
      :is-fetching-next-page="search.isFetchingNextPage.value"
      :has-next-page="search.hasNextPage.value ?? false"
      :error="search.error.value"
      empty-title="找不到符合的發文"
      empty-description="換個關鍵字試試。搜尋只比對發文內容，不含使用者名稱。"
      :on-load-more="() => search.fetchNextPage()"
      :on-retry="() => search.refetch()"
    />
  </section>
</template>
