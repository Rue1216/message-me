/**
 * Query key 的單一來源。
 *
 * <p>Key 決定兩件事：快取如何命中，以及一次寫入之後該讓哪些查詢失效。
 * 若讓字串散落在各個元件裡，遲早會出現「新增留言後動態牆沒更新」這種只在
 * 特定操作順序下才浮現的問題——因為某處把 key 拼錯了一個字。
 *
 * <p>階層設計讓失效可以按範圍進行：
 * `invalidateQueries({ queryKey: postKeys.lists() })` 會一次命中動態牆、搜尋與標籤頁，
 * 因為三者的 key 都以 `['posts', 'list']` 為前綴。
 *
 * <p>`as const` 是必要的：沒有它，TypeScript 會把 key 推斷成 string[]，
 * 失去逐段比對的型別保護。
 */

export const postKeys = {
  all: ['posts'] as const,

  /** 所有列表類查詢的共同前綴——動態牆、搜尋、標籤頁。 */
  lists: () => [...postKeys.all, 'list'] as const,
  feed: () => [...postKeys.lists(), 'feed'] as const,
  search: (keyword: string) => [...postKeys.lists(), 'search', keyword] as const,
  byTag: (tag: string) => [...postKeys.lists(), 'tag', tag] as const,

  details: () => [...postKeys.all, 'detail'] as const,
  detail: (postId: number) => [...postKeys.details(), postId] as const,
}

export const commentKeys = {
  all: ['comments'] as const,
  byPost: (postId: number) => [...commentKeys.all, 'post', postId] as const,
  page: (postId: number, page: number) => [...commentKeys.byPost(postId), page] as const,
}

export const userKeys = {
  all: ['users'] as const,
  me: () => [...userKeys.all, 'me'] as const,
  detail: (userId: number) => [...userKeys.all, 'detail', userId] as const,
  activities: (userId: number, page: number) =>
    [...userKeys.all, 'activities', userId, page] as const,
}

export const tagKeys = {
  all: ['tags'] as const,
  popular: () => [...tagKeys.all, 'popular'] as const,
}
