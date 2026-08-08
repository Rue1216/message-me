<script setup lang="ts">
import { Search } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import EmptyState from '@/components/common/EmptyState.vue'
import PostFeed from '@/components/post/PostFeed.vue'
import PopularTags from '@/components/tag/PopularTags.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppInput from '@/components/ui/AppInput.vue'
import { usePostSearch } from '@/composables/usePostFeed'

/**
 * 搜尋結果。
 *
 * <p>關鍵字取自網址而非元件內的狀態：搜尋結果應該是可以分享、可以加入書籤、
 * 可以用瀏覽器的上一頁回到的——這些都要求它存在於 URL 中。
 * 頁面上的搜尋框只負責把使用者導到這個網址。
 *
 * <p>這裡自備一個搜尋框，而不是仰賴頁首的那個：頁首的搜尋框在 sm 以下是隱藏的，
 * 手機使用者只能從底部導覽進到這一頁——若這頁也沒有輸入框，搜尋在小螢幕上就是一條死路。
 * 同理，熱門標籤側欄在 lg 以下不存在，因此在這裡補上一份（桌機則交給側欄，不重複顯示）。
 */
const route = useRoute()
const router = useRouter()

const keyword = computed(() => (typeof route.query.q === 'string' ? route.query.q : ''))

const search = usePostSearch(keyword)

/** 輸入中的草稿。與網址上的關鍵字分開：使用者打到一半時不該每個字都觸發一次查詢。 */
const draft = ref(keyword.value)

// 網址變了就跟著同步：從別處帶著 ?q= 進來、或按上一頁時，輸入框要顯示當下真正在搜的字
watch(keyword, (value) => {
  draft.value = value
})

function submitSearch(): void {
  const trimmed = draft.value.trim()
  // 與現有關鍵字相同時不重複導航，避免在瀏覽紀錄裡堆出一串一模一樣的項目
  if (trimmed && trimmed !== keyword.value) {
    void router.push({ name: 'search', query: { q: trimmed } })
  }
}
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

    <form
      class="mb-4 flex gap-2"
      role="search"
      @submit.prevent="submitSearch"
    >
      <label
        for="search-keyword"
        class="sr-only"
      >搜尋發文</label>
      <div class="relative flex-1">
        <Search
          class="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
        <AppInput
          id="search-keyword"
          v-model="draft"
          type="search"
          placeholder="搜尋發文內容…"
          class="pl-8"
        />
      </div>
      <AppButton type="submit">
        搜尋
      </AppButton>
    </form>

    <!-- 熱門標籤：桌機由 AppShell 的右側欄負責，這裡只補 lg 以下看不到側欄的情形 -->
    <div class="mb-4 lg:hidden">
      <PopularTags />
    </div>

    <EmptyState
      v-if="!keyword"
      title="輸入關鍵字開始搜尋"
      description="可以搜尋發文內容。想找標籤的話，直接點選熱門標籤或文章上的 #標籤 更快。"
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
