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

/** 頁碼分頁結果。頁碼自 1 起算，與後端 PageResponse 一致。 */
export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/**
 * 游標分頁結果，用於時間軸類的列表。
 *
 * 沒有總筆數與總頁數是刻意的：無限捲動不需要它們，而在大資料表上為了顯示一個數字
 * 去做 COUNT(*) 並不划算。`nextCursor` 對前端不透明，原樣回傳給下一次請求即可。
 */
export interface CursorPageResponse<T> {
  items: T[]
  nextCursor?: string | null
  hasMore: boolean
}

/** 內容作者的顯示資訊，內嵌於發文與留言中，不含手機號碼與電子郵件。 */
export interface Author {
  userId: number
  userName: string
  coverImage?: string | null
  /** 已刪除帳號的內容仍會保留，此時名稱已被匿名化，且不應連往個人頁。 */
  deleted: boolean
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
  deleted: boolean
  createdAt: string
}

export interface Post {
  postId: number
  content: string
  image?: string | null
  commentCount: number
  likeCount: number
  /** 目前登入者是否按過讚；未登入時恆為 false。 */
  likedByMe: boolean
  /** 標籤名稱，不含 `#`。 */
  tags: string[]
  createdAt: string
  updatedAt: string
  author: Author
}

export interface Comment {
  commentId: number
  postId: number
  content: string
  createdAt: string
  /** 與 createdAt 相異即代表被編輯過。 */
  updatedAt: string
  author: Author
}

export interface Tag {
  name: string
  postCount: number
}

/** 個人頁時間軸上的一筆活動——一則發文，或一則留言。 */
export interface Activity {
  type: 'POST' | 'COMMENT'
  activityId: number
  /** 兩種活動都指向一則發文，因此永遠可以連過去。 */
  postId: number
  content: string
  image?: string | null
  /** 僅 POST 有意義。 */
  commentCount: number
  /** 僅 POST 有意義。 */
  likeCount: number
  createdAt: string
  /** 僅 COMMENT 有值：被留言的那則發文的內容摘要。 */
  postExcerpt?: string | null
  /** 僅 COMMENT 有值：被留言的那則發文的作者。 */
  postAuthorName?: string | null
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

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
}

export interface DeleteAccountPayload {
  password: string
}
