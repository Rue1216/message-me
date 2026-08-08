import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import { uploadImage } from '@/api/resources/files'
import AvatarEditorDialog from '@/components/user/AvatarEditorDialog.vue'

vi.mock('@/api/resources/files', async (importOriginal) => ({
  // 常數沿用實作，測試不必自己維護一份會慢慢對不上的副本
  ...(await importOriginal<typeof import('@/api/resources/files')>()),
  uploadImage: vi.fn(),
}))

vi.mock('vue-sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
  Toaster: { template: '<div />' },
}))

/**
 * AppDialog 會把內容 teleport 到 body，包在 wrapper 之外就找不到了。
 * 對話框本身的無障礙行為由 Reka UI 負責，這裡要驗的是挑圖、預覽與儲存的邏輯，
 * 因此把它換成一個只負責攤平兩個 slot 的替身。
 */
const AppDialogStub = {
  props: ['open', 'title', 'description'],
  template: '<div v-if="open"><slot /><slot name="footer" /></div>',
}

const uploadImageMock = vi.mocked(uploadImage)

const EXISTING_AVATAR = '/uploads/existing.jpg'

function mountDialog(image: string | null = null) {
  const onSave = vi.fn().mockResolvedValue(undefined)
  const wrapper = mount(AvatarEditorDialog, {
    props: { open: true, name: '小明', image, onSave },
    global: { stubs: { AppDialog: AppDialogStub } },
  })
  return { wrapper, onSave }
}

type Wrapper = ReturnType<typeof mountDialog>['wrapper']

/** 以文字尋找按鈕：版面調整不該讓這些測試失敗。 */
function button(wrapper: Wrapper, label: string) {
  return wrapper.findAll('button').find((candidate) => candidate.text() === label)
}

async function pickFile(wrapper: Wrapper, size = 1024): Promise<void> {
  const file = new File(['x'], 'avatar.png', { type: 'image/png' })
  Object.defineProperty(file, 'size', { value: size })

  const input = wrapper.find('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

describe('AvatarEditorDialog', () => {
  beforeEach(() => {
    uploadImageMock.mockReset()
    // jsdom 沒有實作 blob 網址，補上可觀察的替身
    URL.createObjectURL = vi.fn(() => 'blob:preview')
    URL.revokeObjectURL = vi.fn()
  })

  it('開啟時顯示目前已儲存的頭像', () => {
    const { wrapper } = mountDialog(EXISTING_AVATAR)

    expect(wrapper.find('img').attributes('src')).toBe(EXISTING_AVATAR)
  })

  it('選檔後立即以本機網址預覽，此時尚未上傳', async () => {
    const { wrapper, onSave } = mountDialog(EXISTING_AVATAR)

    await pickFile(wrapper)

    expect(wrapper.find('img').attributes('src')).toBe('blob:preview')
    expect(uploadImageMock).not.toHaveBeenCalled()
    expect(onSave).not.toHaveBeenCalled()
  })

  it('超過大小上限的檔案不會進入預覽', async () => {
    const { wrapper } = mountDialog(EXISTING_AVATAR)

    await pickFile(wrapper, 6 * 1024 * 1024)

    expect(wrapper.find('img').attributes('src')).toBe(EXISTING_AVATAR)
  })

  it('取消後重新開啟，預覽回到已儲存的頭像並釋放本機網址', async () => {
    const { wrapper, onSave } = mountDialog(EXISTING_AVATAR)

    await pickFile(wrapper)
    await button(wrapper, '取消')?.trigger('click')
    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })

    expect(wrapper.find('img').attributes('src')).toBe(EXISTING_AVATAR)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview')
    expect(onSave).not.toHaveBeenCalled()
  })

  it('沒有頭像時不提供移除', () => {
    const { wrapper } = mountDialog(null)

    expect(button(wrapper, '移除頭像')).toBeUndefined()
  })

  it('移除頭像後儲存，送出的是 null 且不會上傳', async () => {
    const { wrapper, onSave } = mountDialog(EXISTING_AVATAR)

    await button(wrapper, '移除頭像')?.trigger('click')
    await button(wrapper, '儲存')?.trigger('click')
    await flushPromises()

    expect(uploadImageMock).not.toHaveBeenCalled()
    expect(onSave).toHaveBeenCalledWith(null)
    expect(wrapper.emitted('update:open')?.at(-1)).toEqual([false])
  })

  it('儲存時先上傳，再把回傳的路徑交給 onSave', async () => {
    uploadImageMock.mockResolvedValue({ url: '/uploads/new.jpg' })
    const { wrapper, onSave } = mountDialog(null)

    await pickFile(wrapper)
    await button(wrapper, '儲存')?.trigger('click')
    await flushPromises()

    expect(uploadImageMock).toHaveBeenCalledOnce()
    expect(onSave).toHaveBeenCalledWith('/uploads/new.jpg')
    expect(wrapper.emitted('update:open')?.at(-1)).toEqual([false])
  })

  it('儲存失敗時維持開啟，讓使用者可以重試', async () => {
    uploadImageMock.mockRejectedValue(new Error('boom'))
    const { wrapper, onSave } = mountDialog(null)

    await pickFile(wrapper)
    await button(wrapper, '儲存')?.trigger('click')
    await flushPromises()

    expect(onSave).not.toHaveBeenCalled()
    expect(wrapper.emitted('update:open')).toBeUndefined()
  })

  it('沒有任何變更時儲存鈕停用', () => {
    const { wrapper } = mountDialog(EXISTING_AVATAR)

    expect(button(wrapper, '儲存')?.attributes('disabled')).toBeDefined()
  })
})
