import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { VueQueryPlugin } from '@tanstack/vue-query'

import { fetchPopularTags } from '@/api/resources/tags'
import TagInput from '@/components/tag/TagInput.vue'

vi.mock('@/api/resources/tags', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/resources/tags')>()),
  fetchPopularTags: vi.fn(),
}))

const fetchPopularTagsMock = vi.mocked(fetchPopularTags)

async function mountInput(modelValue: string[] = []) {
  const wrapper = mount(TagInput, {
    props: {
      modelValue,
      'onUpdate:modelValue': (value: string[]) => wrapper.setProps({ modelValue: value }),
    },
    global: { plugins: [VueQueryPlugin] },
  })
  await flushPromises()
  return wrapper
}

/** 依序輸入一段文字並按下指定按鍵。 */
async function type(wrapper: Awaited<ReturnType<typeof mountInput>>, text: string, key = 'Enter') {
  const input = wrapper.get('input')
  await input.setValue(text)
  await input.trigger('keydown', { key })
  await flushPromises()
}

describe('TagInput', () => {
  beforeEach(() => {
    fetchPopularTagsMock.mockReset()
    fetchPopularTagsMock.mockResolvedValue([
      { name: '登山', postCount: 5 },
      { name: '美食', postCount: 3 },
    ])
  })

  it.each(['Enter', ',', '，', '、', ' '])('按下 %s 把輸入變成一顆標籤', async (key) => {
    const wrapper = await mountInput()

    await type(wrapper, '露營', key)

    expect(wrapper.props('modelValue')).toEqual(['露營'])
  })

  it('一次貼上多個標籤時全部拆開', async () => {
    const wrapper = await mountInput()

    await type(wrapper, '登山, 露營 攝影')

    expect(wrapper.props('modelValue')).toEqual(['登山', '露營', '攝影'])
  })

  it('全形分隔符一樣拆得開，中文輸入法打出來的本來就是全形', async () => {
    const wrapper = await mountInput()

    await type(wrapper, '登山，露營、攝影')

    expect(wrapper.props('modelValue')).toEqual(['登山', '露營', '攝影'])
  })

  it('顯示正規化後的形式，讓改寫立刻可見', async () => {
    const wrapper = await mountInput()

    await type(wrapper, 'Vue3')

    expect(wrapper.props('modelValue')).toEqual(['vue3'])
    expect(wrapper.text()).toContain('#vue3')
  })

  it('失焦時也把未送出的字變成標籤', async () => {
    const wrapper = await mountInput()

    const input = wrapper.get('input')
    await input.setValue('露營')
    await input.trigger('blur')
    await flushPromises()

    expect(wrapper.props('modelValue')).toEqual(['露營'])
  })

  it('輸入框為空時按 Backspace 刪掉最後一顆', async () => {
    const wrapper = await mountInput(['登山', '美食'])

    await wrapper.get('input').trigger('keydown', { key: 'Backspace' })
    await flushPromises()

    expect(wrapper.props('modelValue')).toEqual(['登山'])
  })

  it('還在打字時 Backspace 不刪標籤', async () => {
    const wrapper = await mountInput(['登山'])

    const input = wrapper.get('input')
    await input.setValue('露')
    await input.trigger('keydown', { key: 'Backspace' })
    await flushPromises()

    expect(wrapper.props('modelValue')).toEqual(['登山'])
  })

  // 注音與拼音用空白選字、用 Enter 確認字詞。組字中攔截這兩個鍵，
  // 使用者就選不了字——這是 zh-TW 產品最容易踩到的一種壞法。
  it.each(['Enter', ' '])('輸入法組字中按 %s 是選字不是送出，不產生標籤', async (key) => {
    const wrapper = await mountInput()

    const input = wrapper.get('input')
    await input.setValue('deng')
    await input.trigger('keydown', { key, isComposing: true })
    await flushPromises()

    expect(wrapper.props('modelValue')).toEqual([])
    expect(input.element.value).toBe('deng')
  })

  it('組字中按 Backspace 刪的是組字中的字，不是前一顆標籤', async () => {
    const wrapper = await mountInput(['登山'])

    // 組字尚未落地時輸入框的 value 還是空的，正是最容易誤刪標籤的時機
    await wrapper.get('input').trigger('keydown', { key: 'Backspace', isComposing: true })
    await flushPromises()

    expect(wrapper.props('modelValue')).toEqual(['登山'])
  })

  it('點移除鈕刪掉指定的那一顆', async () => {
    const wrapper = await mountInput(['登山', '美食'])

    await wrapper.get('button[aria-label="移除標籤 登山"]').trigger('click')
    await flushPromises()

    expect(wrapper.props('modelValue')).toEqual(['美食'])
  })

  it('不合法的字元被擋下並說明原因，輸入內容保留讓使用者可以修改', async () => {
    const wrapper = await mountInput()

    await type(wrapper, '台北101!')

    expect(wrapper.props('modelValue')).toEqual([])
    expect(wrapper.text()).toContain('標籤只能使用文字、數字與底線')
    expect(wrapper.get('input').element.value).toBe('台北101!')
  })

  it('重複的標籤靜默忽略，不顯示錯誤', async () => {
    const wrapper = await mountInput(['登山'])

    await type(wrapper, '登山')

    expect(wrapper.props('modelValue')).toEqual(['登山'])
    expect(wrapper.text()).not.toContain('標籤只能使用')
    expect(wrapper.text()).not.toContain('標籤最多')
    expect(wrapper.get('input').element.value).toBe('')
  })

  it('超過長度上限時擋下並說明', async () => {
    const wrapper = await mountInput()

    await type(wrapper, 'a'.repeat(51))

    expect(wrapper.props('modelValue')).toEqual([])
    expect(wrapper.text()).toContain('標籤不可超過 50 字')
  })

  it('超過數量上限時擋下並說明', async () => {
    const full = Array.from({ length: 10 }, (_, index) => `tag${index}`)
    const wrapper = await mountInput(full)

    await type(wrapper, '再一個')

    expect(wrapper.props('modelValue')).toEqual(full)
    expect(wrapper.text()).toContain('標籤最多 10 個')
  })

  // 錯誤訊息是針對「前一次輸入」說的。任何一次成功的新增或刪除之後它就不再成立，
  // 留在畫面上只會變成對著一顆剛加好的合法標籤指指點點。
  it('改好不合法的輸入再送出，錯誤訊息跟著消失', async () => {
    const wrapper = await mountInput()

    await type(wrapper, '台北101!')
    expect(wrapper.text()).toContain('標籤只能使用文字、數字與底線')

    await type(wrapper, '台北101')

    expect(wrapper.props('modelValue')).toEqual(['台北101'])
    expect(wrapper.text()).not.toContain('標籤只能使用文字、數字與底線')
  })

  it('點熱門標籤加入後，錯誤訊息一樣要消失', async () => {
    const wrapper = await mountInput()

    await type(wrapper, '台北101!')
    expect(wrapper.text()).toContain('標籤只能使用文字、數字與底線')

    await wrapper.get('button[aria-label="加入標籤 登山"]').trigger('click')
    await flushPromises()

    expect(wrapper.props('modelValue')).toEqual(['登山'])
    expect(wrapper.text()).not.toContain('標籤只能使用文字、數字與底線')
  })

  // 重複不是錯誤，這一輪其實什麼都沒有被擋下。舊訊息若沒清掉，
  // 送出的流程還會誤判成「這次也失敗了」而把輸入的字留在框裡
  it('錯誤訊息還在時送出一個重複的標籤，訊息與輸入的字一起清掉', async () => {
    const wrapper = await mountInput(['登山'])

    await type(wrapper, '台北101!')
    expect(wrapper.text()).toContain('標籤只能使用文字、數字與底線')

    await type(wrapper, '登山')

    expect(wrapper.props('modelValue')).toEqual(['登山'])
    expect(wrapper.get('input').element.value).toBe('')
    expect(wrapper.text()).not.toContain('標籤只能使用文字、數字與底線')
  })

  it('刪掉一顆之後，數量上限的錯誤訊息不再成立也要消失', async () => {
    const full = Array.from({ length: 10 }, (_, index) => `tag${index}`)
    const wrapper = await mountInput(full)

    await type(wrapper, '再一個')
    expect(wrapper.text()).toContain('標籤最多 10 個')

    await wrapper.get('button[aria-label="移除標籤 tag0"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('標籤最多 10 個')
  })

  it('點熱門標籤即加入，且該標籤自建議中消失', async () => {
    const wrapper = await mountInput()

    await wrapper.get('button[aria-label="加入標籤 登山"]').trigger('click')
    await flushPromises()

    expect(wrapper.props('modelValue')).toEqual(['登山'])
    expect(wrapper.find('button[aria-label="加入標籤 登山"]').exists()).toBe(false)
    expect(wrapper.find('button[aria-label="加入標籤 美食"]').exists()).toBe(true)
  })

  it('建議最多列 8 個，再多就從輔助變成干擾', async () => {
    // 熱門標籤的請求本身就會拿到 12 個，截斷若失效這裡是唯一看得出來的地方
    fetchPopularTagsMock.mockResolvedValue(
      Array.from({ length: 12 }, (_, index) => ({ name: `tag${index}`, postCount: 12 - index })),
    )
    const wrapper = await mountInput()

    expect(wrapper.findAll('button[aria-label^="加入標籤"]')).toHaveLength(8)
    expect(wrapper.find('button[aria-label="加入標籤 tag7"]').exists()).toBe(true)
    expect(wrapper.find('button[aria-label="加入標籤 tag8"]').exists()).toBe(false)
  })
})
