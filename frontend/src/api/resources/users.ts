import { http, unwrap } from '@/api/client/http'
import type { ApiResponse, CurrentUser, PublicUser, UpdateProfilePayload } from '@/types/api'

/** 取得本人的完整檔案。使用者身分取自權杖，不需帶 ID。 */
export async function fetchCurrentUser(): Promise<CurrentUser> {
  const response = await http.get<ApiResponse<CurrentUser>>('/users/me')
  return unwrap(response.data)
}

/** 更新本人的個人檔案。PUT 為全欄位取代語意：沒帶的欄位即為清空。 */
export async function updateCurrentUser(payload: UpdateProfilePayload): Promise<CurrentUser> {
  const response = await http.put<ApiResponse<CurrentUser>>('/users/me', payload)
  return unwrap(response.data)
}

/** 取得他人的公開檔案，不含手機號碼與電子郵件。 */
export async function fetchPublicUser(userId: number): Promise<PublicUser> {
  const response = await http.get<ApiResponse<PublicUser>>(`/users/${userId}`)
  return unwrap(response.data)
}
