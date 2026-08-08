import { useInfiniteQuery } from '@tanstack/vue-query'
import { computed, type MaybeRefOrGetter, toValue } from 'vue'

import { fetchPosts, searchPosts } from '@/api/resources/posts'
import { fetchPostsByTag } from '@/api/resources/tags'
import { postKeys } from '@/queries/queryKeys'
import type { CursorPageResponse, Post } from '@/types/api'

/** 每次載入的筆數。 */
export const FEED_PAGE_SIZE = 10

type FeedPage = CursorPageResponse<Post>

/**
 * 游標分頁的共用設定。
 *
 * <p>三種列表（動態牆、搜尋、標籤）的分頁行為完全相同，差別只在 key 與抓取函式，
 * 因此把 `getNextPageParam` 這類樣板收在這裡，避免三份幾乎一樣的程式碼各自演化。
 *
 * <p>`initialPageParam` 為 undefined 代表第一頁——與後端「cursor 留空即為第一頁」的契約一致。
 */
function cursorFeedOptions() {
  return {
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage: FeedPage) => lastPage.nextCursor ?? undefined,
  }
}

/**
 * 把分頁結果攤平成單一陣列。
 *
 * <p>無限捲動的畫面只關心「目前累積了哪些發文」，分頁的邊界在哪裡是實作細節。
 */
function flatten(pages: FeedPage[] | undefined): Post[] {
  return pages?.flatMap((page) => page.items) ?? []
}

/** 動態牆：所有人的發文，新到舊。 */
export function usePostFeed() {
  const query = useInfiniteQuery({
    queryKey: postKeys.feed(),
    queryFn: ({ pageParam }) => fetchPosts(pageParam, FEED_PAGE_SIZE),
    ...cursorFeedOptions(),
  })

  return { ...query, posts: computed(() => flatten(query.data.value?.pages)) }
}

/**
 * 搜尋結果。
 *
 * @param keyword 響應式的關鍵字；改變時會自動重新查詢（key 含關鍵字）。
 *                空字串時不發出請求——沒有關鍵字的搜尋沒有意義。
 */
export function usePostSearch(keyword: MaybeRefOrGetter<string>) {
  const trimmed = computed(() => toValue(keyword).trim())

  const query = useInfiniteQuery({
    queryKey: computed(() => postKeys.search(trimmed.value)),
    queryFn: ({ pageParam }) => searchPosts(trimmed.value, pageParam, FEED_PAGE_SIZE),
    enabled: computed(() => trimmed.value.length > 0),
    ...cursorFeedOptions(),
  })

  return { ...query, posts: computed(() => flatten(query.data.value?.pages)) }
}

/** 某個標籤底下的發文。 */
export function usePostsByTag(tag: MaybeRefOrGetter<string>) {
  const name = computed(() => toValue(tag))

  const query = useInfiniteQuery({
    queryKey: computed(() => postKeys.byTag(name.value)),
    queryFn: ({ pageParam }) => fetchPostsByTag(name.value, pageParam, FEED_PAGE_SIZE),
    enabled: computed(() => name.value.length > 0),
    ...cursorFeedOptions(),
  })

  return { ...query, posts: computed(() => flatten(query.data.value?.pages)) }
}
