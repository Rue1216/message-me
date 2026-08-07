import { beforeEach, describe, expect, it, vi } from 'vitest'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import { updateCurrentUser } from '@/api/resources/users'
import { useProfileMutations } from '@/composables/useProfileMutations'
import { commentKeys, postKeys, userKeys } from '@/queries/queryKeys'
import { useAuthStore } from '@/stores/auth'
import type { CurrentUser } from '@/types/api'

vi.mock('@/api/resources/users', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/resources/users')>()),
  updateCurrentUser: vi.fn(),
}))

const updateCurrentUserMock = vi.mocked(updateCurrentUser)

const user: CurrentUser = {
  userId: 7,
  phoneNumber: '0912345678',
  userName: '小明',
  coverImage: '/uploads/old.jpg',
  createdAt: '2026-08-01T10:00:00',
  updatedAt: '2026-08-01T10:00:00',
}

/** 從元件外呼叫 useMutation 需要有 QueryClient 注入，因此借一個空元件當作宿主。 */
function mountProfileMutations(queryClient: QueryClient) {
  let api!: ReturnType<typeof useProfileMutations>
  mount(
    {
      setup() {
        api = useProfileMutations()
        return () => null
      },
    },
    { global: { plugins: [[VueQueryPlugin, { queryClient }]] } },
  )
  return api
}

describe('useProfileMutations', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    window.sessionStorage.clear()
    setActivePinia(createPinia())
    updateCurrentUserMock.mockReset()

    // 關掉重試，失敗的案例才不會等到逾時
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    // 先讓快取裡有東西可以失效——沒有資料的 key 不會被標記
    queryClient.setQueryData(postKeys.feed(), { pages: [], pageParams: [] })
    queryClient.setQueryData(commentKeys.page(1, 1), { items: [] })
    queryClient.setQueryData(userKeys.detail(user.userId), user)

    useAuthStore().signIn({ accessToken: 'token', tokenType: 'Bearer', expiresIn: 7200, user })
  })

  it('更新成功後讓內嵌作者快照的查詢失效', async () => {
    const updated = { ...user, coverImage: '/uploads/new.jpg' }
    updateCurrentUserMock.mockResolvedValue(updated)
    const { update } = mountProfileMutations(queryClient)

    await update.mutateAsync({
      userName: user.userName,
      email: null,
      biography: null,
      coverImage: '/uploads/new.jpg',
    })

    // 發文與留言各自帶著一份作者的名稱與頭像，個人檔案一改，那些副本就全部過期
    expect(queryClient.getQueryState(postKeys.feed())?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(commentKeys.page(1, 1))?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(userKeys.detail(user.userId))?.isInvalidated).toBe(true)
  })

  it('更新成功後把權威結果寫回快取與登入狀態', async () => {
    const updated = { ...user, userName: '小華', coverImage: null }
    updateCurrentUserMock.mockResolvedValue(updated)
    const { update } = mountProfileMutations(queryClient)

    await update.mutateAsync({
      userName: '小華',
      email: null,
      biography: null,
      coverImage: null,
    })

    expect(queryClient.getQueryData(userKeys.me())).toEqual(updated)
    expect(useAuthStore().user?.userName).toBe('小華')
    expect(useAuthStore().user?.coverImage).toBeNull()
  })

  it('更新失敗時不動快取，也不動登入狀態', async () => {
    updateCurrentUserMock.mockRejectedValue(new Error('boom'))
    const { update } = mountProfileMutations(queryClient)

    await expect(
      update.mutateAsync({ userName: '小華', email: null, biography: null, coverImage: null }),
    ).rejects.toThrow()

    expect(queryClient.getQueryState(postKeys.feed())?.isInvalidated).toBe(false)
    expect(useAuthStore().user?.userName).toBe('小明')
  })
})
