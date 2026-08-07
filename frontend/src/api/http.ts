import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'

import { clearSession, readSession } from '@/auth/session-storage'
import type { ApiResponse } from '@/types/api'

/**
 * 全站唯一的 HTTP 用戶端。
 *
 * <p>三件事在這裡集中處理，避免散落到每一支呼叫：
 * <ol>
 *   <li>請求攔截器自動附上 `Authorization: Bearer <token>`。</li>
 *   <li>回應攔截器把後端的 `{ success, error }` 失敗格式與網路層錯誤，
 *       統一轉成 {@link ApiClientError}，讓畫面只需要 `catch` 一種型別。</li>
 *   <li>權杖失效（401）時清除登入狀態並通知外層導回登入頁。</li>
 * </ol>
 */

/** 對外的統一錯誤型別。`code` 供程式分支，`message` 供顯示。 */
export class ApiClientError extends Error {
  readonly code: string
  readonly status: number

  constructor(code: string, message: string, status: number) {
    super(message)
    // 目標為 ES2015 以上時，繼承內建 Error 需自行修正 name，否則堆疊訊息會顯示為 Error
    this.name = 'ApiClientError'
    this.code = code
    this.status = status
  }
}

/** 網路層或未預期錯誤共用的代碼，與後端的 ErrorCode 不重疊。 */
export const NETWORK_ERROR_CODE = 'NETWORK_ERROR'

export const http: AxiosInstance = axios.create({
  // 容器環境由 Nginx 反向代理，本機開發由 Vite 的 proxy 轉發，兩者對前端而言都是同源的 /api
  baseURL: '/api',
  timeout: 15_000,
  headers: { Accept: 'application/json' },
})

type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null

/**
 * 註冊「權杖失效」的處置方式（通常是清空 store 並導回登入頁）。
 *
 * <p>採用註冊而非直接 import router 與 store，是為了切斷
 * `http → router → view → api → http` 的循環相依，同時讓攔截器在測試中可獨立驗證。
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler
}

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const session = readSession()
  if (session) {
    config.headers.set('Authorization', `Bearer ${session.accessToken}`)
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => Promise.reject(toApiClientError(error)),
)

/**
 * 取出成功回應中的 `data`。
 *
 * <p>後端即使在 HTTP 200 也可能回 `success: false`（理論上不會，但契約允許），
 * 這裡一併收斂，讓呼叫端不必重複檢查。
 */
export function unwrap<T>(payload: ApiResponse<T>): T {
  if (!payload.success) {
    throw new ApiClientError(
      payload.error?.code ?? NETWORK_ERROR_CODE,
      payload.error?.message ?? '伺服器回應格式不正確',
      0,
    )
  }
  return payload.data as T
}

function toApiClientError(error: unknown): ApiClientError {
  if (error instanceof ApiClientError) {
    return error
  }
  if (!(error instanceof AxiosError)) {
    return new ApiClientError(NETWORK_ERROR_CODE, '發生未預期的錯誤，請稍後再試', 0)
  }

  const status = error.response?.status ?? 0
  const payload = error.response?.data as ApiResponse<unknown> | undefined

  if (status === 401 && !isAuthEndpoint(error.config?.url)) {
    // 只在「原本帶著權杖去打受保護端點」時登出。
    // 登入與註冊本來就會以 401 表示帳密錯誤，若一併觸發登出流程，
    // 使用者每打錯一次密碼就會被導向一次登入頁，反而蓋掉錯誤訊息。
    clearSession()
    unauthorizedHandler?.()
  }

  if (payload?.error) {
    return new ApiClientError(payload.error.code, payload.error.message, status)
  }
  if (error.code === AxiosError.ECONNABORTED || error.code === AxiosError.ETIMEDOUT) {
    return new ApiClientError(NETWORK_ERROR_CODE, '連線逾時，請稍後再試', status)
  }
  if (status === 0) {
    return new ApiClientError(NETWORK_ERROR_CODE, '無法連線至伺服器，請確認網路狀態', status)
  }
  return new ApiClientError(NETWORK_ERROR_CODE, `伺服器回應異常（HTTP ${status}）`, status)
}

function isAuthEndpoint(url: string | undefined): boolean {
  return (url ?? '').startsWith('/auth/')
}
