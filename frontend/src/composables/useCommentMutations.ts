import { useMutation, useQueryClient } from '@tanstack/vue-query'

import { createComment, deleteComment, updateComment } from '@/api/resources/comments'
import { commentKeys, postKeys, userKeys } from '@/queries/queryKeys'

/**
 * 留言的新增、編輯與刪除。
 *
 * <p>三者都會連動發文的 `commentCount`（由後端在 SP 的交易內維護），
 * 因此成功後除了留言列表，也必須讓該篇發文的快取失效——否則畫面上會出現
 * 「留言多了一則，但計數還是舊的」這種只差一個數字、卻很顯眼的不一致。
 */
export function useCommentMutations(postId: number) {
  const queryClient = useQueryClient()

  function invalidateAffected(): void {
    void queryClient.invalidateQueries({ queryKey: commentKeys.byPost(postId) })
    // 留言數變了：詳情頁與所有列表都要更新
    void queryClient.invalidateQueries({ queryKey: postKeys.detail(postId) })
    void queryClient.invalidateQueries({ queryKey: postKeys.lists() })
    // 個人頁的合併動態含留言
    void queryClient.invalidateQueries({ queryKey: userKeys.all })
  }

  const create = useMutation({
    mutationFn: (content: string) => createComment(postId, content),
    onSuccess: invalidateAffected,
  })

  const update = useMutation({
    mutationFn: ({ commentId, content }: { commentId: number; content: string }) =>
      updateComment(commentId, content),
    onSuccess: invalidateAffected,
  })

  const remove = useMutation({
    mutationFn: (commentId: number) => deleteComment(commentId),
    onSuccess: invalidateAffected,
  })

  return { create, update, remove }
}
