import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { clearSession, readSession, writeSession, type AuthSession } from '@/auth/session-storage'
import type { CurrentUser, LoginResult } from '@/types/api'

/**
 * 登入狀態。
 *
 * <p>唯一的真實來源是 `sessionStorage`（見 `auth/session-storage.ts`），store 只是它的
 * 響應式檢視：每次變更都同步寫回，因此 Axios 攔截器直接讀 storage 也不會拿到過期的權杖。
 * 建立時先行還原，使頁面重整後仍維持登入。
 */
export const useAuthStore = defineStore('auth', () => {
  const session = ref<AuthSession | null>(readSession())

  const user = computed<CurrentUser | null>(() => session.value?.user ?? null)
  const isAuthenticated = computed(() => session.value !== null)
  /** 目前登入者的 ID；未登入時為 null。用於判斷「這則發文是不是我的」。 */
  const currentUserId = computed<number | null>(() => session.value?.user.userId ?? null)

  /** 登入成功後寫入權杖與個人檔案。 */
  function signIn(result: LoginResult): void {
    const next: AuthSession = { accessToken: result.accessToken, user: result.user }
    session.value = next
    writeSession(next)
  }

  /** 登出，或權杖失效時清除狀態。重複呼叫是安全的。 */
  function signOut(): void {
    session.value = null
    clearSession()
  }

  /** 個人檔案更新後同步顯示用的資料，不影響權杖。 */
  function setUser(updated: CurrentUser): void {
    if (!session.value) {
      return
    }
    const next: AuthSession = { accessToken: session.value.accessToken, user: updated }
    session.value = next
    writeSession(next)
  }

  return { session, user, isAuthenticated, currentUserId, signIn, signOut, setUser }
})
