import type { FormItemRule } from 'naive-ui'

import type { Validator } from '@/utils/validation/types'

/**
 * 把純函式驗證器包成 Naive UI 的表單規則。
 *
 * <p>驗證邏輯本身與 UI 函式庫無關（見 `validation/user.ts`、`validation/post.ts`），
 * 因此能被單獨測試；這層轉接只負責把 null / 訊息翻譯成 Naive UI 期望的 true / Error。
 *
 * <p>元件庫的相依刻意只集中在這一個檔案——更換 UI 框架時，要改的就只有這裡。
 */
export function toFormRule(validate: Validator, trigger: string[] = ['blur', 'input']): FormItemRule {
  return {
    trigger,
    validator(_rule: FormItemRule, value: string | null): true | Error {
      const message = validate(value ?? '')
      return message === null ? true : new Error(message)
    },
  }
}
