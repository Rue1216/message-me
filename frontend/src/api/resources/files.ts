import { http, unwrap } from '@/api/client/http'
import type { ApiResponse, UploadedImage } from '@/types/api'

/** 圖片格式白名單，與後端 ImageStorageService 的內容嗅探結果一致。 */
export const ACCEPTED_IMAGE_TYPES = 'image/jpeg,image/png,image/webp'

/** 單檔大小上限，與後端 `app.upload.max-file-size-bytes` 一致。 */
export const MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024

/**
 * 上傳圖片，取得可填入發文或個人檔案的相對路徑。
 *
 * <p>上傳與使用分兩步：這裡只回傳路徑，由呼叫端決定要不要真的送出。
 * 前端這一層的型別與大小檢查只是為了少一次來回，真正的把關在後端
 * （內容嗅探、UUID 重新命名、落點驗證）。
 */
export async function uploadImage(file: File): Promise<UploadedImage> {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<ApiResponse<UploadedImage>>('/files/images', form)
  return unwrap(response.data)
}
