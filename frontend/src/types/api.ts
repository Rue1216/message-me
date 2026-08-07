/**
 * 後端 API 的資料型別。
 *
 * 這些型別與 `backend/src/main/java/com/esun/social/presentation/dto` 下的 record 一一對應。
 * 後端以 `spring.jackson.default-property-inclusion: non_null` 序列化，因此值為 null 的欄位
 * 不會出現在 JSON 中——選填欄位一律宣告為 `?: string | null`，兩種缺值形式都涵蓋。
 */

/** 全站統一的回應外殼；`data` 與 `error` 互斥。 */
export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: ApiErrorPayload
}

export interface ApiErrorPayload {
  /** 穩定的錯誤代碼，對應後端的 ErrorCode enum，是前端唯一應據以分支的欄位。 */
  code: string
  /** 給使用者看的說明文字，措辭可能隨版本調整，不應被程式判斷。 */
  message: string
}

/** 分頁結果。頁碼自 1 起算，與後端 PageResponse 一致。 */
export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** 內容作者的顯示資訊，內嵌於發文與留言中，不含手機號碼與電子郵件。 */
export interface Author {
  userId: number
  userName: string
  coverImage?: string | null
}

/** 本人的完整個人檔案，僅在註冊、登入與 `/api/users/me` 的回應中出現。 */
export interface CurrentUser {
  userId: number
  phoneNumber: string
  userName: string
  email?: string | null
  coverImage?: string | null
  biography?: string | null
  createdAt: string
  updatedAt: string
}

/** 他人的公開檔案。 */
export interface PublicUser {
  userId: number
  userName: string
  coverImage?: string | null
  biography?: string | null
  createdAt: string
}

export interface Post {
  postId: number
  content: string
  image?: string | null
  commentCount: number
  createdAt: string
  updatedAt: string
  author: Author
}

export interface Comment {
  commentId: number
  postId: number
  content: string
  createdAt: string
  author: Author
}

/** 登入結果。`user` 一併回傳，省去登入後立刻再打一次 `/api/users/me`。 */
export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: CurrentUser
}

export interface UploadedImage {
  /** 相對路徑，例如 `/uploads/<UUID>.jpg`，可直接填入發文的 image 或個人檔案的 coverImage。 */
  url: string
}

/** 新增與編輯發文共用的請求主體（PUT 為全欄位取代語意）。 */
export interface PostPayload {
  content: string
  image?: string | null
}

export interface RegisterPayload {
  phoneNumber: string
  userName: string
  password: string
  email?: string | null
}

export interface LoginPayload {
  phoneNumber: string
  password: string
}

export interface UpdateProfilePayload {
  userName: string
  email?: string | null
  biography?: string | null
  coverImage?: string | null
}
