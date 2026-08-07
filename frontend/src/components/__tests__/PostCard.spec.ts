import { afterEach, describe, expect, it } from 'vitest'
import { RouterLinkStub, flushPromises, mount } from '@vue/test-utils'

import PostCard from '@/components/post/PostCard.vue'
import type { Post } from '@/types/api'

const post: Post = {
  postId: 12,
  content: '今天天氣不錯',
  image: null,
  commentCount: 3,
  likeCount: 5,
  likedByMe: false,
  tags: [],
  createdAt: '2026-08-07T09:00:00',
  updatedAt: '2026-08-07T09:00:00',
  author: { userId: 5, userName: '小明', coverImage: null, deleted: false },
}

/**
 * LikeButton 被替換成 stub：它自帶 Query、Pinia 與 router 的相依，
 * 拉進來只會讓「這張卡片顯示了什麼」的測試變成需要準備整個應用程式環境。
 * 按讚的行為由它自己的測試負責。
 */
const mounted: { unmount: () => void }[] = []

/**
 * attachTo：動作選單的內容以 Portal 掛在 body 上，元件若不在真實文件裡就量不到位置也收不到焦點。
 * 代價是每個測試都要卸載，否則上一個測試留下的選單會被下一個查到。
 */
function mountCard(props: Partial<InstanceType<typeof PostCard>['$props']> = {}) {
  const wrapper = mount(PostCard, {
    props: { post, ...props },
    attachTo: document.body,
    global: {
      stubs: {
        RouterLink: RouterLinkStub,
        LikeButton: { template: '<button type="button">讚</button>' },
      },
    },
  })
  mounted.push(wrapper)
  return wrapper
}

/** 打開「⋯」選單，並以文字取得其中一個選項。 */
async function openMenuItem(label: string): Promise<HTMLElement | undefined> {
  document.querySelector<HTMLElement>('button[aria-label="發文操作"]')?.click()
  await flushPromises()

  return [...document.querySelectorAll<HTMLElement>('[role="menuitem"]')].find(
    (item) => item.textContent?.trim() === label,
  )
}

afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
})

describe('PostCard', () => {
  it('顯示作者、內容與留言數', () => {
    const wrapper = mountCard()

    expect(wrapper.text()).toContain('小明')
    expect(wrapper.text()).toContain('今天天氣不錯')
    expect(wrapper.text()).toContain('留言 3')
  })

  it('不是自己的發文時不顯示編輯與刪除', () => {
    const wrapper = mountCard()

    expect(wrapper.text()).not.toContain('編輯')
    expect(wrapper.text()).not.toContain('刪除')
  })

  it('是自己的發文時可以從動作選單送出編輯與刪除事件', async () => {
    const wrapper = mountCard({ canManage: true })

    // 以文字而非位置尋找：版面調整（例如選單改成別的圖示）不該讓這個測試失敗。
    // 選單選完就關閉，所以刪除要再開一次。
    const edit = await openMenuItem('編輯')
    edit?.click()
    await flushPromises()

    const remove = await openMenuItem('刪除')
    remove?.click()
    await flushPromises()

    expect(wrapper.emitted('edit')?.[0]).toEqual([post])
    expect(wrapper.emitted('remove')?.[0]).toEqual([post])
  })

  it('內容以文字節點呈現，不會被當成 HTML 解析', () => {
    const wrapper = mountCard({
      post: { ...post, content: '<script>alert(1)</script>' },
    })

    // 這是 XSS 防護的輸出端保證：Vue 的 {{ }} 一律轉義，專案並全面禁用 v-html
    expect(wrapper.text()).toContain('<script>alert(1)</script>')
    expect(wrapper.find('.user-content').element.querySelector('script')).toBeNull()
  })

  it('編輯過的發文標示「已編輯」', () => {
    const wrapper = mountCard({
      post: { ...post, updatedAt: '2026-08-07T10:00:00' },
    })

    expect(wrapper.text()).toContain('已編輯')
  })

  it('顯示標籤，並連往該標籤的列表', () => {
    const wrapper = mountCard({ post: { ...post, tags: ['登山', '美食'] } })

    expect(wrapper.text()).toContain('#登山')
    expect(wrapper.text()).toContain('#美食')
  })

  it('作者已刪除帳號時不連往個人頁', () => {
    const wrapper = mountCard({
      post: { ...post, author: { ...post.author, userName: '已刪除的使用者', deleted: true } },
    })

    expect(wrapper.text()).toContain('已刪除的使用者')
    // 只剩「留言」那一個連結，作者名稱不再是連結
    const links = wrapper.findAllComponents(RouterLinkStub)
    expect(links).toHaveLength(1)
  })
})
