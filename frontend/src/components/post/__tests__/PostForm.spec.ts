import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { VueQueryPlugin } from '@tanstack/vue-query'

import PostForm from '@/components/post/PostForm.vue'
import type { PostPayload } from '@/types/api'

vi.mock('@/api/resources/tags', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/resources/tags')>()),
  fetchPopularTags: vi.fn().mockResolvedValue([]),
}))

const DRAFT_KEY = 'draft:test'

/** ImageUploader 自帶檔案讀取與預覽，這裡要驗的是內容與標籤怎麼被送出。 */
const ImageUploaderStub = { template: '<div data-test="uploader" />' }

async function mountForm(props: Record<string, unknown> = {}) {
  const wrapper = mount(PostForm, {
    props,
    global: {
      plugins: [VueQueryPlugin],
      stubs: { ImageUploader: ImageUploaderStub },
    },
  })
  await flushPromises()
  return wrapper
}

/** 表單裡有兩個輸入元件：textarea 是內文，input 是標籤。 */
async function addTag(wrapper: Awaited<ReturnType<typeof mountForm>>, tag: string) {
  const input = wrapper.get('input')
  await input.setValue(tag)
  await input.trigger('keydown', { key: 'Enter' })
  await flushPromises()
}

/**
 * 依文字找送出鈕，而不是 `wrapper.get('button')`——
 * 加了標籤之後，畫面上第一個 button 會是那顆標籤的移除鈕。
 */
async function submitForm(wrapper: Awaited<ReturnType<typeof mountForm>>) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes('送出'))
  expect(button).toBeTruthy()
  await button!.trigger('click')
  await flushPromises()
}

function lastSubmit(wrapper: Awaited<ReturnType<typeof mountForm>>): PostPayload {
  const events = wrapper.emitted('submit')
  expect(events).toBeTruthy()
  return events!.at(-1)![0] as PostPayload
}

describe('PostForm', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('標籤隨 payload 一起送出', async () => {
    const wrapper = await mountForm()

    await wrapper.get('textarea').setValue('今天去走走')
    await addTag(wrapper, '登山')
    await submitForm(wrapper)

    expect(lastSubmit(wrapper)).toEqual({ content: '今天去走走', image: null, tags: ['登山'] })
  })

  it('沒有標籤時送出空陣列，不阻擋發文', async () => {
    const wrapper = await mountForm()

    await wrapper.get('textarea').setValue('今天去走走')
    await submitForm(wrapper)

    expect(lastSubmit(wrapper).tags).toEqual([])
  })

  it('編輯時帶入既有的標籤', async () => {
    const wrapper = await mountForm({ initialContent: '原文', initialTags: ['登山'] })

    expect(wrapper.text()).toContain('#登山')
  })

  it('草稿以 JSON 保存內容與標籤', async () => {
    const wrapper = await mountForm({ draftKey: DRAFT_KEY })

    await wrapper.get('textarea').setValue('打到一半')
    await addTag(wrapper, '登山')

    expect(JSON.parse(sessionStorage.getItem(DRAFT_KEY)!)).toEqual({
      content: '打到一半',
      tags: ['登山'],
    })
  })

  it('只有標籤、內文還空著時也保存草稿', async () => {
    const wrapper = await mountForm({ draftKey: DRAFT_KEY })

    await addTag(wrapper, '登山')

    expect(JSON.parse(sessionStorage.getItem(DRAFT_KEY)!)).toEqual({ content: '', tags: ['登山'] })
  })

  it('讀回 JSON 草稿', async () => {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ content: '打到一半', tags: ['登山'] }))

    const wrapper = await mountForm({ draftKey: DRAFT_KEY })

    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('打到一半')
    expect(wrapper.text()).toContain('#登山')
  })

  /**
   * sessionStorage 是使用者改得動的地方，草稿又只是一段字串——
   * 讀回來的東西不該因為被人動過手腳，就把非字串一路帶進畫面與送出的 payload。
   */
  it('草稿的標籤混進非字串時只讀回字串', async () => {
    sessionStorage.setItem(
      DRAFT_KEY,
      JSON.stringify({ content: '打到一半', tags: ['ok', 1, null, { a: 1 }] }),
    )

    const wrapper = await mountForm({ draftKey: DRAFT_KEY })

    expect(wrapper.text()).toContain('#ok')
    expect(wrapper.text()).not.toContain('#1')

    await submitForm(wrapper)

    expect(lastSubmit(wrapper).tags).toEqual(['ok'])
  })

  it('改格式之前留下的純文字草稿仍當成內文讀回，不會消失', async () => {
    sessionStorage.setItem(DRAFT_KEY, '舊格式的草稿')

    const wrapper = await mountForm({ draftKey: DRAFT_KEY })

    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('舊格式的草稿')
  })

  it('reset 一併清空標籤與草稿', async () => {
    const wrapper = await mountForm({ draftKey: DRAFT_KEY })

    await wrapper.get('textarea').setValue('打到一半')
    await addTag(wrapper, '登山')
    // 打到一半的標籤與被擋下的原因都是 TagInput 的內部狀態，把 tags 陣列清空碰不到它們。
    // TagInput 又沒有被 re-key，reset() 之後不會重建——沒有明著請它清一次的話，
    // 這兩樣會原封不動地跟著下一篇、下下篇發文
    await addTag(wrapper, '台北101!')
    expect(wrapper.text()).toContain('標籤只能使用文字、數字與底線')
    ;(wrapper.vm as unknown as { reset: () => void }).reset()
    await flushPromises()

    expect(wrapper.text()).not.toContain('#登山')
    expect(wrapper.get('input').element.value).toBe('')
    expect(wrapper.text()).not.toContain('標籤只能使用文字、數字與底線')
    expect(sessionStorage.getItem(DRAFT_KEY)).toBeNull()
  })
})
