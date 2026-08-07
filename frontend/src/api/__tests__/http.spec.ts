import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'

import { ApiClientError, http, setUnauthorizedHandler, unwrap } from '@/api/client/http'
import { readSession, writeSession } from '@/auth/session-storage'
import type { CurrentUser } from '@/types/api'

const user: CurrentUser = {
  userId: 1,
  phoneNumber: '0912345678',
  userName: '小明',
  createdAt: '2026-08-01T10:00:00',
  updatedAt: '2026-08-01T10:00:00',
}

type MockReply = { status: number; data: unknown }

const originalAdapter = http.defaults.adapter
let seenConfigs: InternalAxiosRequestConfig[] = []

/**
 * 以自訂 adapter 取代真正的網路層。
 *
 * Axios 的攔截器在 adapter 之外執行，因此換掉 adapter 就能在不發出任何請求的情況下，
 * 完整驗證「請求帶不帶權杖」與「401 之後做了什麼」。
 */
function mockReply(reply: (config: InternalAxiosRequestConfig) => MockReply): void {
  http.defaults.adapter = async (config) => {
    const typedConfig = config as InternalAxiosRequestConfig
    seenConfigs.push(typedConfig)
    const { status, data } = reply(typedConfig)
    const response = {
      data,
      status,
      statusText: '',
      headers: {},
      config: typedConfig,
    } as AxiosResponse
    if (status >= 200 && status < 300) {
      return response
    }
    throw new AxiosError('request failed', String(status), typedConfig, null, response)
  }
}

describe('http', () => {
  beforeEach(() => {
    seenConfigs = []
    window.sessionStorage.clear()
    setUnauthorizedHandler(null)
  })

  afterEach(() => {
    http.defaults.adapter = originalAdapter
    setUnauthorizedHandler(null)
  })

  it('已登入時自動附上 Authorization 標頭', async () => {
    writeSession({ accessToken: 'token-abc', user })
    mockReply(() => ({ status: 200, data: { success: true, data: null } }))

    await http.get('/posts')

    expect(seenConfigs[0]?.headers.get('Authorization')).toBe('Bearer token-abc')
  })

  it('未登入時不附上 Authorization 標頭', async () => {
    mockReply(() => ({ status: 200, data: { success: true, data: null } }))

    await http.get('/posts')

    expect(seenConfigs[0]?.headers.get('Authorization')).toBeUndefined()
  })

  it('把後端的錯誤格式轉成帶有錯誤代碼的 ApiClientError', async () => {
    mockReply(() => ({
      status: 403,
      data: { success: false, error: { code: 'FORBIDDEN', message: '沒有權限執行這項操作' } },
    }))

    const error = await http.get('/posts/1').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiClientError)
    expect((error as ApiClientError).code).toBe('FORBIDDEN')
    expect((error as ApiClientError).status).toBe(403)
    expect((error as ApiClientError).message).toBe('沒有權限執行這項操作')
  })

  it('受保護端點回 401 時清除登入狀態並通知外層', async () => {
    writeSession({ accessToken: 'token-abc', user })
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    mockReply(() => ({
      status: 401,
      data: { success: false, error: { code: 'UNAUTHORIZED', message: '請先登入' } },
    }))

    await http.get('/users/me').catch(() => undefined)

    expect(readSession()).toBeNull()
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('登入端點回 401 只是帳密錯誤，不應觸發登出流程', async () => {
    writeSession({ accessToken: 'token-abc', user })
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    mockReply(() => ({
      status: 401,
      data: { success: false, error: { code: 'INVALID_CREDENTIALS', message: '手機號碼或密碼不正確' } },
    }))

    const error = await http
      .post('/auth/login', { phoneNumber: '0912345678', password: 'wrong-password' })
      .catch((caught: unknown) => caught)

    expect((error as ApiClientError).code).toBe('INVALID_CREDENTIALS')
    expect(readSession()).not.toBeNull()
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('連不上伺服器時給出可讀的訊息而不是原始例外', async () => {
    http.defaults.adapter = async () => {
      throw new AxiosError('Network Error', AxiosError.ERR_NETWORK)
    }

    const error = await http.get('/posts').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiClientError)
    expect((error as ApiClientError).message).toContain('無法連線至伺服器')
  })
})

describe('unwrap', () => {
  it('取出成功回應的 data', () => {
    expect(unwrap({ success: true, data: { postId: 1 } })).toEqual({ postId: 1 })
  })

  it('success 為 false 時擲出 ApiClientError', () => {
    expect(() => unwrap({ success: false, error: { code: 'NOT_FOUND', message: '找不到' } })).toThrow(
      ApiClientError,
    )
  })
})
