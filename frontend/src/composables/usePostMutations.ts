import { useMutation, useQueryClient, type InfiniteData } from '@tanstack/vue-query'

import { createPost, deletePost, updatePost } from '@/api/resources/posts'
import { postKeys, tagKeys, userKeys } from '@/queries/queryKeys'
import type { CursorPageResponse, Post, PostPayload } from '@/types/api'

type FeedData = InfiniteData<CursorPageResponse<Post>>

/**
 * 發文的新增、編輯與刪除。
 *
 * <p>與按讚不同，這三者**不採樂觀更新**：
 * 新增與編輯的結果包含後端正規化過的標籤與資料庫產生的時間，前端無法憑空造出正確的樣子；
 * 硬要猜就會出現「送出後標籤跟送出去的長得不一樣，一秒後才更正」這種比等待更糟的閃動。
 *
 * <p>刪除則是例外中的例外——它的結果完全可預測（那則發文消失），
 * 而且刪除後若整頁重抓，使用者的捲動位置會跳掉。因此刪除採樂觀移除。
 */
export function usePostMutations() {
  const queryClient = useQueryClient()

  /** 內容變動後，讓所有可能受影響的查詢重新取得。 */
  function invalidateAffected(postId?: number): void {
    void queryClient.invalidateQueries({ queryKey: postKeys.lists() })
    // 標籤的使用次數可能改變（新增/移除標籤、刪除發文）
    void queryClient.invalidateQueries({ queryKey: tagKeys.all })
    // 個人頁的合併動態含發文
    void queryClient.invalidateQueries({ queryKey: userKeys.all })
    if (postId !== undefined) {
      void queryClient.invalidateQueries({ queryKey: postKeys.detail(postId) })
    }
  }

  const create = useMutation({
    mutationFn: (payload: PostPayload) => createPost(payload),
    onSuccess: () => invalidateAffected(),
  })

  const update = useMutation({
    mutationFn: ({ postId, payload }: { postId: number; payload: PostPayload }) =>
      updatePost(postId, payload),
    onSuccess: (updated) => {
      // 先把權威結果寫回快取，詳情頁立即正確；再讓列表重新取得
      queryClient.setQueryData(postKeys.detail(updated.postId), updated)
      invalidateAffected(updated.postId)
    },
  })

  const remove = useMutation({
    mutationFn: (postId: number) => deletePost(postId),

    async onMutate(postId) {
      await queryClient.cancelQueries({ queryKey: postKeys.lists() })
      const previousLists = queryClient.getQueriesData<FeedData>({ queryKey: postKeys.lists() })

      // 就地移除，捲動位置不受影響——這正是不整頁重抓的理由
      queryClient.setQueriesData<FeedData>({ queryKey: postKeys.lists() }, (data) => {
        if (!data) {
          return data
        }
        return {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            items: page.items.filter((post) => post.postId !== postId),
          })),
        }
      })

      return { previousLists }
    },

    onError(_error, _postId, context) {
      context?.previousLists.forEach(([key, data]) => queryClient.setQueryData(key, data))
    },

    onSettled: (_data, _error, postId) => invalidateAffected(postId),
  })

  return { create, update, remove }
}
