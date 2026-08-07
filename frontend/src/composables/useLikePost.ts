import { useMutation, useQueryClient, type InfiniteData } from '@tanstack/vue-query'

import { likePost, unlikePost } from '@/api/resources/posts'
import { postKeys } from '@/queries/queryKeys'
import type { CursorPageResponse, Post } from '@/types/api'

type FeedData = InfiniteData<CursorPageResponse<Post>>

/**
 * 按讚 / 取消按讚，採樂觀更新。
 *
 * <p><strong>為什麼這裡值得樂觀更新</strong><br>
 * 按讚是最頻繁、最輕量的互動，等一個來回才看到圖示變色會讓整個介面顯得遲鈍。
 * 而且這個操作在後端是冪等的（複合主鍵 + INSERT IGNORE，見 sp_post_like），
 * 重試或重複送出都不會讓計數失準——這正是敢於先改畫面的前提。
 *
 * <p><strong>三個回呼各自的職責</strong><br>
 * onMutate：取消進行中的查詢（否則稍後回來的舊資料會蓋掉我們剛寫的），
 *           留下快照供回滾，然後就地更新快取。
 * onError： 用快照還原。使用者看到的是「按下去、彈回來」，而不是一個永遠錯誤的狀態。
 * onSettled：無論成敗都讓相關查詢失效，以伺服器的真實值收斂
 *           （別人同時按的讚也會在這時一併反映）。
 */
export function useLikePost() {
  const queryClient = useQueryClient()

  /** 對快取中每一份含有這則發文的資料套用同一個轉換。 */
  function patchPostEverywhere(postId: number, patch: (post: Post) => Post): void {
    // 列表：動態牆、搜尋、標籤頁——三者的 key 共用 lists() 前綴，一次涵蓋
    queryClient.setQueriesData<FeedData>({ queryKey: postKeys.lists() }, (data) => {
      if (!data) {
        return data
      }
      return {
        ...data,
        pages: data.pages.map((page) => ({
          ...page,
          items: page.items.map((post) => (post.postId === postId ? patch(post) : post)),
        })),
      }
    })

    // 詳情頁：同一則發文可能正開著
    queryClient.setQueryData<Post>(postKeys.detail(postId), (post) =>
      post ? patch(post) : post,
    )
  }

  return useMutation({
    mutationFn: ({ postId, liked }: { postId: number; liked: boolean }) =>
      liked ? unlikePost(postId) : likePost(postId),

    async onMutate({ postId, liked }) {
      await queryClient.cancelQueries({ queryKey: postKeys.all })

      const previousLists = queryClient.getQueriesData<FeedData>({ queryKey: postKeys.lists() })
      const previousDetail = queryClient.getQueryData<Post>(postKeys.detail(postId))

      patchPostEverywhere(postId, (post) => ({
        ...post,
        likedByMe: !liked,
        // 夾在 0 以下不可能發生，但快取若因任何原因失準，畫面也不該出現負數
        likeCount: Math.max(0, post.likeCount + (liked ? -1 : 1)),
      }))

      return { previousLists, previousDetail, postId }
    },

    onError(_error, _variables, context) {
      if (!context) {
        return
      }
      context.previousLists.forEach(([key, data]) => queryClient.setQueryData(key, data))
      queryClient.setQueryData(postKeys.detail(context.postId), context.previousDetail)
    },

    onSettled(_data, _error, { postId }) {
      void queryClient.invalidateQueries({ queryKey: postKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: postKeys.detail(postId) })
    },
  })
}
