import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import { useFormValidation } from '@/composables/useFormValidation'

function setup() {
  const model = ref({ name: '', email: '' })
  const form = useFormValidation(model, {
    name: (value) => (value.trim() ? null : '請填寫名稱'),
    email: (value) => (value.includes('@') ? null : '格式不正確'),
  })
  return { model, form }
}

describe('useFormValidation', () => {
  it('尚未失焦前不顯示錯誤，避免使用者還在打字就被指正', () => {
    const { form } = setup()

    form.revalidate('name')

    expect(form.errors.name).toBeUndefined()
  })

  it('失焦後顯示錯誤', () => {
    const { form } = setup()

    form.validateOnBlur('name')

    expect(form.errors.name).toBe('請填寫名稱')
  })

  it('顯示過錯誤之後改為即時更新，讓使用者看得到自己修好了', () => {
    const { model, form } = setup()

    form.validateOnBlur('name')
    expect(form.errors.name).toBe('請填寫名稱')

    model.value.name = '小明'
    form.revalidate('name')

    expect(form.errors.name).toBeNull()
  })

  it('validateAll 驗證全部欄位並回報整體結果', () => {
    const { model, form } = setup()

    expect(form.validateAll()).toBe(false)
    expect(form.errors.name).toBe('請填寫名稱')
    expect(form.errors.email).toBe('格式不正確')

    model.value.name = '小明'
    model.value.email = 'a@example.com'

    expect(form.validateAll()).toBe(true)
    expect(form.errors.name).toBeNull()
    expect(form.errors.email).toBeNull()
  })

  it('reset 同時清掉錯誤與「已顯示過」的狀態', () => {
    const { form } = setup()

    form.validateAll()
    form.reset()

    expect(form.errors.name).toBeNull()

    // 重置後回到「還沒失焦」的狀態：revalidate 不應該讓錯誤重新出現
    form.revalidate('name')
    expect(form.errors.name).toBeNull()
  })
})
