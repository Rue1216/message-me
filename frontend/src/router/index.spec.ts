import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { LoginResult } from '@/types/api'

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

describe('導航守衛', () => {
  beforeEach(async () => {
    window.sessionStorage.clear()
    setActivePinia(createPinia())
    await router.replace('/')
    await router.isReady()
  })

  it('未登入者進入受保護頁面會被導向登入頁，並記住原本的目的地', async () => {
    await router.push('/profile')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/profile')
  })

  it('已登入者可以進入受保護頁面', async () => {
    useAuthStore().signIn(loginResult)

    await router.push('/profile')

    expect(router.currentRoute.value.name).toBe('profile')
  })

  it('已登入者不需要再看登入頁，直接回動態牆', async () => {
    useAuthStore().signIn(loginResult)

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('home')
  })

  it('動態牆與發文詳情不需要登入即可瀏覽', async () => {
    await router.push('/posts/12')
    expect(router.currentRoute.value.name).toBe('post-detail')

    await router.push('/')
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('未知網址落到 404 頁面', async () => {
    await router.push('/no-such-page')

    expect(router.currentRoute.value.name).toBe('not-found')
  })
})
