/**
 * 發文與留言的表單驗證規則。
 *
 * <p>與 `validation/user.ts` 同一套原則：上限值對齊後端 Bean Validation，
 * 前端驗證只為及早回饋，不是安全邊界。
 */

import type { Validator } from '@/utils/validation/types'

export const POST_CONTENT_MAX_LENGTH = 5000
export const COMMENT_CONTENT_MAX_LENGTH = 1000

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
