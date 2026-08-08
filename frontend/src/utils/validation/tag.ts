/**
 * 標籤的正規化與驗證規則。
 *
 * <p>與 `validation/post.ts` 同一套原則：上限值與字元集對齊後端的 TagNormalizer，
 * 前端驗證只為及早回饋，不是安全邊界。
 */

export const TAG_MAX_LENGTH = 50
export const MAX_TAGS_PER_POST = 10

/** 只允許文字、數字與底線。`u` 旗標讓 \p{L} 涵蓋中日韓字元，與後端一致。 */
const ALLOWED_TAG = /^[\p{L}\p{N}_]+$/u

/**
 * 送出時的分隔符：半形與全形逗號、頓號，以及任何空白。
 * 中文輸入法下打出的是 `，` 與 `、`，只認半形逗號會讓大多數人打不出第二個標籤。
 */
export const TAG_SEPARATORS = /[,，、\s]+/

/**
 * 去除前後空白並轉為小寫。
 *
 * <p>用 `toLowerCase` 而非 `toLocaleLowerCase`：前者不受地區設定影響，
 * 與後端的 `toLowerCase(Locale.ROOT)` 是同一個規則。
 */
export function normaliseTag(value: string): string {
  return value.trim().toLowerCase()
}

/**
 * 檢查一個標籤能否加入。重複不在這裡判斷——那不是錯誤，
 * 而是「使用者要的結果已經在畫面上了」，交由呼叫端靜默忽略。
 *
 * @param value    使用者剛輸入、尚未正規化的一段文字
 * @param existing 已經選好的標籤（正規化後的形式）
 * @returns 錯誤訊息，或通過時的 null
 */
export function validateTag(value: string, existing: string[]): string | null {
  const tag = normaliseTag(value)
  if (!tag) {
    return null
  }
  // 數量上限先檢查：已經滿了的時候，回報「字元不合法」會讓使用者
  // 埋頭修改一個其實無論如何都加不進去的標籤
  if (existing.length >= MAX_TAGS_PER_POST) {
    return `標籤最多 ${MAX_TAGS_PER_POST} 個`
  }
  if (tag.length > TAG_MAX_LENGTH) {
    return `標籤不可超過 ${TAG_MAX_LENGTH} 字`
  }
  if (!ALLOWED_TAG.test(tag)) {
    return '標籤只能使用文字、數字與底線'
  }
  return null
}
