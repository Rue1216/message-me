import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import { login } from '@/api/resources/auth'
import { ApiClientError } from '@/api/client/http'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import type { LoginResult } from '@/types/api'

vi.mock('@/api/resources/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
}))

/**
 * 提示訊息在測試中不需要真的渲染出來。
 *
 * 改版前這裡必須把頁面包進 NMessageProvider 才能呼叫 useMessage()；
 * 現在的 useToast 不依賴 provider，只需要把底層的 vue-sonner 換成空實作即可。
 */
vi.mock('vue-sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
  Toaster: { template: '<div />' },
}))

const loginMock = vi.mocked(login)

const loginResult: LoginResult = {
  accessToken: 'token-abc',
  tokenType: 'Bearer',
  expiresIn: 7200,
  user: {
    userId: 1,
    phoneNumber: '0912345678',
    userName: '小明',
    createdAt: '2026-08-01T10:00:00',
    updatedAt: '2026-08-01T10:00:00',
  },
}

async function mountLoginView() {
  await router.replace('/login')
  await router.isReady()
  const wrapper = mount(LoginView, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

async function fillAndSubmit(
  wrapper: Awaited<ReturnType<typeof mountLoginView>>,
  phoneNumber: string,
  password: string,
) {
  const inputs = wrapper.findAll('input')
  await inputs[0]?.setValue(phoneNumber)
  await inputs[1]?.setValue(password)
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

describe('LoginView', () => {
  beforeEach(async () => {
    loginMock.mockReset()
    window.sessionStorage.clear()
    setActivePinia(createPinia())
    await router.replace('/')
  })

  it('欄位空白時不送出請求，並顯示提示', async () => {
    const wrapper = await mountLoginView()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(loginMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('請填寫手機號碼')
  })

  it('登入成功後寫入登入狀態並導向動態牆', async () => {
    loginMock.mockResolvedValue(loginResult)
    const wrapper = await mountLoginView()

    await fillAndSubmit(wrapper, ' 0912345678 ', 'password123')

    // 手機號碼前後的空白會被修剪，密碼則原樣送出
    expect(loginMock).toHaveBeenCalledWith({ phoneNumber: '0912345678', password: 'password123' })
    expect(useAuthStore().isAuthenticated).toBe(true)
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('帳密錯誤時停留在原頁，不寫入登入狀態', async () => {
    loginMock.mockRejectedValue(
      new ApiClientError('INVALID_CREDENTIALS', '手機號碼或密碼不正確', 401),
    )
    const wrapper = await mountLoginView()

    await fillAndSubmit(wrapper, '0912345678', 'wrong-password')

    expect(useAuthStore().isAuthenticated).toBe(false)
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
