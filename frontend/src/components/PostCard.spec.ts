import { describe, expect, it } from 'vitest'
import { RouterLinkStub, mount } from '@vue/test-utils'

import PostCard from '@/components/PostCard.vue'
import type { Post } from '@/types/api'

const post: Post = {
  postId: 12,
  content: '今天天氣不錯',
  image: null,
  commentCount: 3,
  createdAt: '2026-08-07T09:00:00',
  updatedAt: '2026-08-07T09:00:00',
  author: { userId: 5, userName: '小明', coverImage: null },
}

function mountCard(props: Partial<InstanceType<typeof PostCard>['$props']> = {}) {
  return mount(PostCard, {
    props: { post, ...props },
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
}

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

  it('是自己的發文時可以送出編輯與刪除事件', async () => {
    const wrapper = mountCard({ canManage: true })
    const buttons = wrapper.findAll('button')

    await buttons[0]?.trigger('click')
    await buttons[1]?.trigger('click')

    expect(wrapper.emitted('edit')?.[0]).toEqual([post])
    expect(wrapper.emitted('remove')?.[0]).toEqual([post])
  })

  it('內容以文字節點呈現，不會被當成 HTML 解析', () => {
    const wrapper = mountCard({
      post: { ...post, content: '<script>alert(1)</script>' },
    })

    // 這是 XSS 防護的輸出端保證：Vue 的 {{ }} 一律轉義，專案並全面禁用 v-html
    expect(wrapper.text()).toContain('<script>alert(1)</script>')
    expect(wrapper.find('.post-card__content').element.querySelector('script')).toBeNull()
  })

  it('編輯過的發文標示「已編輯」', () => {
    const wrapper = mountCard({
      post: { ...post, updatedAt: '2026-08-07T10:00:00' },
    })

    expect(wrapper.text()).toContain('已編輯')
  })
})
