import { http, unwrap } from '@/api/client/http'
import type { ApiResponse, CursorPageResponse, Post, Tag } from '@/types/api'

/** 熱門標籤，依使用次數由多到少。公開。 */
export async function fetchPopularTags(limit = 10): Promise<Tag[]> {
  const response = await http.get<ApiResponse<Tag[]>>('/tags/popular', { params: { limit } })
  return unwrap(response.data)
}

/**
 * 某標籤底下的發文，分頁方式與動態牆相同。公開。
 *
 * 標籤名稱會出現在網址路徑中，因此必須經過 encodeURIComponent——
 * 中文標籤與其中可能出現的保留字元都需要編碼。
 */
export async function fetchPostsByTag(
  name: string,
  cursor: string | undefined,
  size: number,
): Promise<CursorPageResponse<Post>> {
  const response = await http.get<ApiResponse<CursorPageResponse<Post>>>(
    `/tags/${encodeURIComponent(name)}/posts`,
    { params: { cursor, size } },
  )
  return unwrap(response.data)
}
