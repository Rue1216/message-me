import type { FormItemRule } from 'naive-ui'

/**
 * 表單驗證規則。
 *
 * <p>每條規則都與後端 DTO 的 Bean Validation 對齊（`presentation/dto/request/*.java`），
 * 上限值刻意寫在同一處而非散落各表單，改動時只需要對照後端一次。
 *
 * <p>前端驗證只為了讓使用者及早看到問題，**不是安全邊界**——真正的把關在後端，
 * 繞過瀏覽器直接打 API 一樣會被擋下。
 */

export const PHONE_NUMBER_PATTERN = /^09\d{8}$/
export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 100
export const USER_NAME_MAX_LENGTH = 50
export const EMAIL_MAX_LENGTH = 255
export const BIOGRAPHY_MAX_LENGTH = 500
export const POST_CONTENT_MAX_LENGTH = 5000
export const COMMENT_CONTENT_MAX_LENGTH = 1000

/** 通過時回傳 null，否則回傳要顯示給使用者的訊息。 */
export type Validator = (value: string) => string | null

export const validatePhoneNumber: Validator = (value) => {
  const trimmed = value.trim()
  if (!trimmed) {
    return '請填寫手機號碼'
  }
  if (!PHONE_NUMBER_PATTERN.test(trimmed)) {
    return '手機號碼格式應為 09 開頭的 10 位數字'
  }
  return null
}

export const validateUserName: Validator = (value) => {
  const trimmed = value.trim()
  if (!trimmed) {
    return '請填寫使用者名稱'
  }
  if (trimmed.length > USER_NAME_MAX_LENGTH) {
    return `使用者名稱不可超過 ${USER_NAME_MAX_LENGTH} 字`
  }
  return null
}

export const validatePassword: Validator = (value) => {
  if (!value) {
    return '請填寫密碼'
  }
  // 密碼不 trim：前後空白也是使用者刻意輸入的字元，剪掉會讓登入與註冊算出不同的雜湊
  if (value.length < PASSWORD_MIN_LENGTH || value.length > PASSWORD_MAX_LENGTH) {
    return `密碼長度需介於 ${PASSWORD_MIN_LENGTH} 至 ${PASSWORD_MAX_LENGTH} 字元`
  }
  return null
}

/** 電子郵件為選填，空字串視為未填。 */
export const validateEmail: Validator = (value) => {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  if (trimmed.length > EMAIL_MAX_LENGTH) {
    return `電子郵件不可超過 ${EMAIL_MAX_LENGTH} 字`
  }
  // 只做基本形狀檢查：過於嚴格的正規表達式會誤擋合法位址，真正的驗證是寄一封信過去
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
    return '電子郵件格式不正確'
  }
  return null
}

export const validateBiography: Validator = (value) => {
  if (value.length > BIOGRAPHY_MAX_LENGTH) {
    return `自我介紹不可超過 ${BIOGRAPHY_MAX_LENGTH} 字`
  }
  return null
}

export const validatePostContent: Validator = (value) => {
  const trimmed = value.trim()
  if (!trimmed) {
    return '請填寫發文內容'
  }
  if (trimmed.length > POST_CONTENT_MAX_LENGTH) {
    return `發文內容不可超過 ${POST_CONTENT_MAX_LENGTH} 字`
  }
  return null
}

export const validateCommentContent: Validator = (value) => {
  const trimmed = value.trim()
  if (!trimmed) {
    return '請填寫留言內容'
  }
  if (trimmed.length > COMMENT_CONTENT_MAX_LENGTH) {
    return `留言內容不可超過 ${COMMENT_CONTENT_MAX_LENGTH} 字`
  }
  return null
}

/**
 * 把純函式驗證器包成 Naive UI 的表單規則。
 *
 * <p>驗證邏輯本身與 UI 函式庫無關，因此能被單獨測試；這層轉接只負責把 null / 訊息
 * 翻譯成 Naive UI 期望的 true / Error。
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
