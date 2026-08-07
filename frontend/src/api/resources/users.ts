import { http, unwrap } from '@/api/client/http'
import type {
  Activity,
  ApiResponse,
  ChangePasswordPayload,
  CurrentUser,
  DeleteAccountPayload,
  PageResponse,
  PublicUser,
  UpdateProfilePayload,
} from '@/types/api'

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

/**
 * 某使用者的發文與留言合併時間軸，新到舊。公開。
 *
 * 這裡用頁碼分頁而非動態牆的游標分頁：資料來自兩張資料表的 UNION，
 * 複合游標脆弱且難以驗證，而個人頁的資料量受單一使用者的產出所限。
 */
export async function fetchUserActivities(
  userId: number,
  page: number,
  size: number,
): Promise<PageResponse<Activity>> {
  const response = await http.get<ApiResponse<PageResponse<Activity>>>(
    `/users/${userId}/activities`,
    { params: { page, size } },
  )
  return unwrap(response.data)
}

/** 修改密碼。需提供目前的密碼——僅憑權杖不足以授權變更憑證。 */
export async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  await http.put<ApiResponse<void>>('/users/me/password', payload)
}

/**
 * 刪除帳號。
 *
 * 後端採軟刪除並匿名化：發文與留言會保留，作者顯示為「已刪除的使用者」。
 * DELETE 帶請求主體並不常見，但這裡確實需要密碼確認，而把密碼放進查詢字串
 * 會讓它出現在伺服器日誌與瀏覽記錄中。
 */
export async function deleteAccount(payload: DeleteAccountPayload): Promise<void> {
  await http.delete<ApiResponse<void>>('/users/me', { data: payload })
}
