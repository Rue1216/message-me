import { http, unwrap } from '@/api/http'
import type { ApiResponse, CurrentUser, LoginPayload, LoginResult, RegisterPayload } from '@/types/api'

/** 註冊。成功回 201，帶回新建立的個人檔案，但不含權杖。 */
export async function register(payload: RegisterPayload): Promise<CurrentUser> {
  const response = await http.post<ApiResponse<CurrentUser>>('/auth/register', payload)
  return unwrap(response.data)
}

/** 登入，取得 JWT 與登入者的個人檔案。 */
export async function login(payload: LoginPayload): Promise<LoginResult> {
  const response = await http.post<ApiResponse<LoginResult>>('/auth/login', payload)
  return unwrap(response.data)
}
