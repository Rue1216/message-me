import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { readSession, writeSession } from '@/auth/session-storage'
import { useAuthStore } from '@/stores/auth'
import type { CurrentUser, LoginResult } from '@/types/api'

const user: CurrentUser = {
  userId: 42,
  phoneNumber: '0912345678',
  userName: '小明',
  createdAt: '2026-08-01T10:00:00',
  updatedAt: '2026-08-01T10:00:00',
}

const loginResult: LoginResult = {
  accessToken: 'token-abc',
  tokenType: 'Bearer',
  expiresIn: 7200,
  user,
}

describe('authStore', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    setActivePinia(createPinia())
  })

  it('初始為未登入', () => {
    const auth = useAuthStore()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.user).toBeNull()
    expect(auth.currentUserId).toBeNull()
  })

  it('登入後保存權杖與使用者，並寫入 sessionStorage', () => {
    const auth = useAuthStore()

    auth.signIn(loginResult)

    expect(auth.isAuthenticated).toBe(true)
    expect(auth.currentUserId).toBe(42)
    expect(readSession()?.accessToken).toBe('token-abc')
  })

  it('登出後清空狀態與 sessionStorage', () => {
    const auth = useAuthStore()
    auth.signIn(loginResult)

    auth.signOut()

    expect(auth.isAuthenticated).toBe(false)
    expect(readSession()).toBeNull()
  })

  it('重複登出不會出錯', () => {
    const auth = useAuthStore()

    auth.signOut()
    auth.signOut()

    expect(auth.isAuthenticated).toBe(false)
  })

  it('建立時還原 sessionStorage 中的登入狀態，使重整後不必重新登入', () => {
    writeSession({ accessToken: 'token-abc', user })

    const auth = useAuthStore()

    expect(auth.isAuthenticated).toBe(true)
    expect(auth.user?.userName).toBe('小明')
  })

  it('更新個人檔案後同步顯示資料，但保留原本的權杖', () => {
    const auth = useAuthStore()
    auth.signIn(loginResult)

    auth.setUser({ ...user, userName: '小華' })

    expect(auth.user?.userName).toBe('小華')
    expect(readSession()?.accessToken).toBe('token-abc')
    expect(readSession()?.user.userName).toBe('小華')
  })

  it('未登入時更新個人檔案不會憑空建立登入狀態', () => {
    const auth = useAuthStore()

    auth.setUser(user)

    expect(auth.isAuthenticated).toBe(false)
    expect(readSession()).toBeNull()
  })
})
