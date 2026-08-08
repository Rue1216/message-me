import { http, unwrap } from '@/api/client/http'
import type { ApiResponse, CursorPageResponse, Post, PostPayload } from '@/types/api'

/**
 * 動態牆，新到舊。公開。
 *
 * @param cursor 上一頁回應的 `nextCursor`；第一頁傳 undefined。
 *               它對前端不透明，不應被解析或組裝——只負責原樣帶回去。
 */
export async function fetchPosts(cursor: string | undefined, size: number): Promise<CursorPageResponse<Post>> {
  const response = await http.get<ApiResponse<CursorPageResponse<Post>>>('/posts', {
    params: { cursor, size },
  })
  return unwrap(response.data)
}

/** 關鍵字搜尋，分頁方式與動態牆相同。公開。 */
export async function searchPosts(
  keyword: string,
  cursor: string | undefined,
  size: number,
): Promise<CursorPageResponse<Post>> {
  const response = await http.get<ApiResponse<CursorPageResponse<Post>>>('/posts/search', {
    params: { q: keyword, cursor, size },
  })
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

/**
 * 編輯發文。僅本人可編輯，且為全欄位取代——不帶 image 即為移除圖片。
 *
 * 標籤同樣走全欄位取代：`tags` 由請求主體帶入（使用者在標籤輸入框指定，不從內文解析），
 * 因此送空陣列就等於把這則發文的標籤全部清掉。要保留原有標籤，編輯表單必須把它們一併帶回來。
 */
export async function updatePost(postId: number, payload: PostPayload): Promise<Post> {
  const response = await http.put<ApiResponse<Post>>(`/posts/${postId}`, payload)
  return unwrap(response.data)
}

/** 刪除發文。後端於 Stored Procedure 內以 Transaction 連帶刪除其留言、按讚與標籤關聯。 */
export async function deletePost(postId: number): Promise<void> {
  await http.delete<ApiResponse<void>>(`/posts/${postId}`)
}

/**
 * 按讚。冪等：重複呼叫不會讓計數失準，因此樂觀更新可以安心重試。
 *
 * @returns 這篇發文的最新狀態（含 likeCount 與 likedByMe）
 */
export async function likePost(postId: number): Promise<Post> {
  const response = await http.post<ApiResponse<Post>>(`/posts/${postId}/likes`)
  return unwrap(response.data)
}

/** 取消按讚。同樣冪等。 */
export async function unlikePost(postId: number): Promise<Post> {
  const response = await http.delete<ApiResponse<Post>>(`/posts/${postId}/likes`)
  return unwrap(response.data)
}
