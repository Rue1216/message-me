import { beforeEach, describe, expect, it } from 'vitest'

import { clearSession, readSession, writeSession, type AuthSession } from '@/auth/session-storage'
import type { CurrentUser } from '@/types/api'

const user: CurrentUser = {
  userId: 7,
  phoneNumber: '0912345678',
  userName: '小明',
  createdAt: '2026-08-01T10:00:00',
  updatedAt: '2026-08-01T10:00:00',
}

const session: AuthSession = { accessToken: 'token-abc', user }

describe('session-storage', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
  })

  it('尚未登入時回傳 null', () => {
    expect(readSession()).toBeNull()
  })

  it('寫入後可讀回相同內容', () => {
    writeSession(session)

    expect(readSession()).toEqual(session)
  })

  it('清除後回到未登入狀態', () => {
    writeSession(session)
    clearSession()

    expect(readSession()).toBeNull()
  })

  it('內容不是合法 JSON 時視為未登入', () => {
    window.sessionStorage.setItem('message-me.session', '{壞掉的內容')

    expect(readSession()).toBeNull()
  })

  it('內容結構不符時視為未登入，不讓殘缺資料流入應用程式', () => {
    window.sessionStorage.setItem('message-me.session', JSON.stringify({ accessToken: 'x' }))

    expect(readSession()).toBeNull()
  })

  it('權杖為空字串時視為未登入', () => {
    window.sessionStorage.setItem('message-me.session', JSON.stringify({ accessToken: '', user }))

    expect(readSession()).toBeNull()
  })
})
