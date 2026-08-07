import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia, setActivePinia } from 'pinia'

import { fetchPopularTags } from '@/api/resources/tags'
import router from '@/router'
import SearchView from '@/views/SearchView.vue'

vi.mock('@/api/resources/posts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/resources/posts')>()),
  searchPosts: vi.fn().mockResolvedValue({ items: [], nextCursor: null, hasMore: false }),
}))

vi.mock('@/api/resources/tags', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/resources/tags')>()),
  fetchPopularTags: vi.fn(),
}))

vi.mock('vue-sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
  Toaster: { template: '<div />' },
}))

/**
 * 列表本身有自己的測試，這裡要驗的是「這一頁能不能發起搜尋」——
 * 拉進真正的 PostFeed 只會連帶需要 IntersectionObserver 與整套發文的 mutation 環境。
 */
const PostFeedStub = { template: '<div data-test="feed" />' }

const fetchPopularTagsMock = vi.mocked(fetchPopularTags)

async function mountSearchView(query = '') {
  await router.replace(query ? `/search?q=${encodeURIComponent(query)}` : '/search')
  await router.isReady()
  const wrapper = mount(SearchView, {
    global: { plugins: [router, VueQueryPlugin], stubs: { PostFeed: PostFeedStub } },
  })
  await flushPromises()
  return wrapper
}

describe('SearchView', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    fetchPopularTagsMock.mockReset()
    fetchPopularTagsMock.mockResolvedValue([{ name: '登山', postCount: 3 }])
    await router.replace('/')
  })

  it('沒有關鍵字時仍提供搜尋框，讓小螢幕使用者有輸入的地方', async () => {
    const wrapper = await mountSearchView()

    expect(wrapper.find('input[type="search"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('輸入關鍵字開始搜尋')
  })

  it('網址上的關鍵字會帶入搜尋框', async () => {
    const wrapper = await mountSearchView('登山')

    expect(wrapper.find<HTMLInputElement>('input[type="search"]').element.value).toBe('登山')
  })

  it('送出搜尋會把關鍵字寫進網址，前後空白會被修剪', async () => {
    const wrapper = await mountSearchView()

    await wrapper.find('input[type="search"]').setValue('  露營  ')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.query.q).toBe('露營')
  })

  it('關鍵字沒變時不重複導航', async () => {
    const wrapper = await mountSearchView('登山')
    const push = vi.spyOn(router, 'push')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(push).not.toHaveBeenCalled()
    push.mockRestore()
  })

  it('頁面內附熱門標籤，小螢幕看不到側欄時也找得到', async () => {
    const wrapper = await mountSearchView()

    expect(wrapper.text()).toContain('熱門標籤')
    expect(wrapper.text()).toContain('#登山')
  })
})
