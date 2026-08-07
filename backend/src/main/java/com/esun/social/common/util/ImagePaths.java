package com.esun.social.common.util;

import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import java.util.regex.Pattern;

/**
 * 圖片路徑的格式把關。
 *
 * <p>資料庫只存本站上傳後產生的相對路徑，格式固定為 {@code /uploads/<UUID>.<副檔名>}。
 * 若放任呼叫端寫入任意字串，發文的 {@code image} 欄位就成了一個可控的 URL 輸出點：
 * {@code javascript:} 開頭的值在某些渲染方式下會變成 XSS，外部網址則能被當成追蹤像素，
 * {@code ../} 更可能在後續處理中造成路徑穿越。
 *
 * <p>白名單比對放在這裡而非 Bean Validation，是因為留言、發文、個人封面三處都要用同一套規則。
 */
public final class ImagePaths {

    private static final Pattern UPLOADED_IMAGE =
            Pattern.compile("^/uploads/[A-Za-z0-9-]{1,64}\\.(jpg|jpeg|png|webp)$");

    private ImagePaths() {}

    /**
     * @param path 圖片路徑，可為 {@code null} 或空字串（代表沒有圖片）
     * @return 通過檢查的路徑，或 {@code null}
     * @throws BusinessException 路徑不是本站上傳產生的格式
     */
    public static String requireUploadedOrNull(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.trim();
        if (!UPLOADED_IMAGE.matcher(trimmed).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "圖片路徑必須是本站上傳後取得的位址");
        }
        return trimmed;
    }
}
