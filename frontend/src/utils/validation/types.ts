/** 驗證器的共用型別，與任何 UI 函式庫無關。 */

/** 通過時回傳 null，否則回傳要顯示給使用者的訊息。 */
export type Validator = (value: string) => string | null
