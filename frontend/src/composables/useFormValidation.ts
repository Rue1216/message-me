import { reactive, type Ref } from 'vue'

import type { Validator } from '@/utils/validation/types'

/**
 * 表單驗證。
 *
 * <p>取代原本 Naive UI 的 `n-form` + `FormRules`。改用自己的一小段程式碼而非再引入一個
 * 表單套件，是因為需求就只有這些：對欄位套用一個 `(value) => string | null` 的函式、
 * 記住錯誤訊息、送出前全部驗一次。驗證規則本身（`utils/validation/*`）完全沒有動過，
 * 它們從一開始就與 UI 函式庫無關——這正是當初把它們寫成純函式的用意。
 *
 * <p><strong>何時顯示錯誤</strong>：欄位失焦（blur）之後才顯示，而不是邊打邊紅。
 * 使用者還在輸入手機號碼的第三碼時就告訴他「格式不對」只是干擾；
 * 但一旦顯示過錯誤，之後就改為即時更新，讓他能立刻看到自己修好了沒有。
 */
export function useFormValidation<T extends Record<string, string>>(
  model: Ref<T>,
  rules: Partial<Record<keyof T, Validator>>,
) {
  const errors = reactive({}) as Record<keyof T, string | null>
  const shown = reactive({}) as Record<keyof T, boolean>

  function run(field: keyof T): string | null {
    const rule = rules[field]
    return rule ? rule(model.value[field] ?? '') : null
  }

  /** 欄位失焦時呼叫：從這一刻起這個欄位開始顯示錯誤。 */
  function validateOnBlur(field: keyof T): void {
    shown[field] = true
    errors[field] = run(field)
  }

  /** 內容變動時呼叫：只更新已經顯示過錯誤的欄位。 */
  function revalidate(field: keyof T): void {
    if (shown[field]) {
      errors[field] = run(field)
    }
  }

  /**
   * 送出前驗證全部欄位。
   *
   * @returns 全部通過為 true
   */
  function validateAll(): boolean {
    let valid = true
    for (const field of Object.keys(rules) as (keyof T)[]) {
      shown[field] = true
      errors[field] = run(field)
      if (errors[field]) {
        valid = false
      }
    }
    return valid
  }

  function reset(): void {
    for (const field of Object.keys(rules) as (keyof T)[]) {
      errors[field] = null
      shown[field] = false
    }
  }

  return { errors, validateOnBlur, revalidate, validateAll, reset }
}
