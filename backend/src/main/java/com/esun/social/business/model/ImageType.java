package com.esun.social.business.model;

/**
 * 允許上傳的圖片格式，以檔案開頭的位元組特徵（magic bytes）辨識。
 *
 * <p><strong>為什麼不看副檔名或 Content-Type</strong><br>
 * 兩者都由上傳端提供，改個名字或改個標頭就能繞過。把 {@code shell.php} 改名成
 * {@code cat.jpg} 再送出，只靠副檔名的檢查完全攔不住。位元組特徵則來自檔案本身。
 *
 * <p>存檔時的副檔名一律由這裡判定的格式決定，不採用使用者提供的檔名——
 * 因此就算判斷有誤，落地的也只會是這三種副檔名之一。
 */
public enum ImageType {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp");

    /** 判斷格式所需的最少位元組數（WEBP 的特徵字串在第 8..11 位元組）。 */
    public static final int HEADER_LENGTH = 12;

    private final String extension;
    private final String contentType;

    ImageType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /**
     * 依檔案開頭的位元組判斷格式。
     *
     * @param header 檔案的前幾個位元組
     * @return 對應的格式；不是支援的圖片時回傳 {@code null}
     */
    public static ImageType detect(byte[] header) {
        if (header == null || header.length < HEADER_LENGTH) {
            return null;
        }
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return PNG;
        }
        // RIFF....WEBP
        if (startsWith(header, 0x52, 0x49, 0x46, 0x46)
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P') {
            return WEBP;
        }
        return null;
    }

    private static boolean startsWith(byte[] header, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            if ((header[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
