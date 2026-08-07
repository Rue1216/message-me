import { http, unwrap } from '@/api/http'
import type { ApiResponse, PageResponse, Post, PostPayload } from '@/types/api'

/** 動態牆分頁，新到舊。頁碼自 1 起算。公開。 */
export async function fetchPosts(page: number, size: number): Promise<PageResponse<Post>> {
  const response = await http.get<ApiResponse<PageResponse<Post>>>('/posts', { params: { page, size } })
  return unwrap(response.data)
}

/** 單篇發文。公開。 */
export async function fetchPost(postId: number): Promise<Post> {
  const response = await http.get<ApiResponse<Post>>(`/posts/${postId}`)
  return unwrap(response.data)
}

export async function createPost(payload: PostPayload): Promise<Post> {
  const response = await http.post<ApiResponse<Post>>('/posts', payload)
  return unwrap(response.data)
}

/** 編輯發文。僅本人可編輯，且為全欄位取代——不帶 image 即為移除圖片。 */
export async function updatePost(postId: number, payload: PostPayload): Promise<Post> {
  const response = await http.put<ApiResponse<Post>>(`/posts/${postId}`, payload)
  return unwrap(response.data)
}

/** 刪除發文。後端於 Stored Procedure 內以 Transaction 連帶刪除其留言。 */
export async function deletePost(postId: number): Promise<void> {
  await http.delete<ApiResponse<void>>(`/posts/${postId}`)
}
