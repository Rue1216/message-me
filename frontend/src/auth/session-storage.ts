import type { CurrentUser } from '@/types/api'

/**
 * 登入狀態的持久化。
 *
 * <p>設計文件 §8.3：權杖存於 `sessionStorage` 而非 `localStorage`——後者在關閉瀏覽器後
 * 仍會保留，共用電腦上風險較高；`sessionStorage` 於分頁關閉時即失效，同時又能讓
 * 頁面重整後不必重新登入。
 *
 * <p>這個模組刻意不依賴 Pinia 與 Axios：`api/http.ts` 的請求攔截器與 `stores/auth.ts`
 * 都要讀寫它，若它反過來依賴任何一方就會形成循環相依。
 */

const STORAGE_KEY = 'message-me.session'

export interface AuthSession {
  accessToken: string
  user: CurrentUser
}

/**
 * 讀取目前的登入狀態。
 *
 * 內容可能被使用者手動竄改或殘留自舊版格式，因此解析失敗時一律當作未登入並清除，
 * 而不是讓例外往外擴散把整個應用程式打掛。
 */
export function readSession(): AuthSession | null {
  let raw: string | null
  try {
    raw = window.sessionStorage.getItem(STORAGE_KEY)
  } catch {
    // 瀏覽器停用儲存空間（例如部分無痕模式）時，退化為僅存於記憶體的行為
    return null
  }
  if (!raw) {
    return null
  }
  try {
    const parsed: unknown = JSON.parse(raw)
    return isAuthSession(parsed) ? parsed : null
  } catch {
    clearSession()
    return null
  }
}

export function writeSession(session: AuthSession): void {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // 寫入失敗不影響本次操作，只是重整後需要重新登入
  }
}

export function clearSession(): void {
  try {
    window.sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // 同上：清不掉也不該讓登出流程失敗
  }
}

function isAuthSession(value: unknown): value is AuthSession {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Partial<AuthSession>
  return (
    typeof candidate.accessToken === 'string' &&
    candidate.accessToken.length > 0 &&
    typeof candidate.user === 'object' &&
    candidate.user !== null &&
    typeof candidate.user.userId === 'number'
  )
}
