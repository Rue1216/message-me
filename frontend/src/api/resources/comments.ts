import { http, unwrap } from '@/api/client/http'
import type { ApiResponse, Comment, PageResponse } from '@/types/api'

/** 單篇發文的留言分頁，舊到新。公開。 */
export async function fetchComments(
  postId: number,
  page: number,
  size: number,
): Promise<PageResponse<Comment>> {
  const response = await http.get<ApiResponse<PageResponse<Comment>>>(`/posts/${postId}/comments`, {
    params: { page, size },
  })
  return unwrap(response.data)
}

export async function createComment(postId: number, content: string): Promise<Comment> {
  const response = await http.post<ApiResponse<Comment>>(`/posts/${postId}/comments`, { content })
  return unwrap(response.data)
}

/**
 * 刪除留言。
 *
 * 路徑只需要留言 ID：留言 ID 已足以定位，要求同時提供發文 ID 只會多一個對不上就出錯的機會。
 */
export async function deleteComment(commentId: number): Promise<void> {
  await http.delete<ApiResponse<void>>(`/comments/${commentId}`)
}
