package com.esun.social.business.service;

import com.esun.social.business.model.ImageType;
import com.esun.social.common.config.UploadProperties;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 圖片上傳。
 *
 * <h2>四道防線</h2>
 * <ol>
 *   <li><strong>大小上限</strong>：Servlet 容器先擋一層（{@code spring.servlet.multipart}），
 *       這裡再檢查一次，避免設定被改動後無人察覺。</li>
 *   <li><strong>內容嗅探</strong>：以檔案開頭的位元組判斷格式，不看副檔名也不看 Content-Type
 *       ——那兩者都是上傳端說了算。</li>
 *   <li><strong>重新命名</strong>：檔名一律換成 UUID，副檔名取自嗅探結果。使用者提供的原始檔名
 *       完全不參與路徑組成，{@code ../../etc/passwd} 這類輸入因此無從發揮。</li>
 *   <li><strong>落點驗證</strong>：寫入前確認正規化後的路徑仍在上傳目錄之內。</li>
 * </ol>
 *
 * <p>另外，圖片由 Nginx 以純靜態檔的方式提供，該路徑下不掛任何直譯器，
 * 即使有辦法放進一個可執行檔也不會被執行。
 */
@Service
public class ImageStorageService {

    private final Path uploadDirectory;
    private final String publicBasePath;
    private final long maxFileSizeBytes;

    public ImageStorageService(UploadProperties properties) throws IOException {
        this.uploadDirectory = Path.of(properties.directory()).toAbsolutePath().normalize();
        this.publicBasePath = properties.publicBasePath();
        this.maxFileSizeBytes = properties.maxFileSizeBytes();
        Files.createDirectories(uploadDirectory);
    }

    /**
     * 存下一張圖片。
     *
     * @return 可直接寫入資料庫並提供給前端的相對路徑，例如 {@code /uploads/<UUID>.jpg}
     * @throws BusinessException 檔案為空、超過大小上限，或不是支援的圖片格式
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "請選擇要上傳的圖片");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "圖片大小不可超過 " + maxFileSizeBytes / 1024 / 1024 + " MB");
        }

        try (InputStream content = file.getInputStream()) {
            byte[] header = content.readNBytes(ImageType.HEADER_LENGTH);
            ImageType imageType = ImageType.detect(header);
            if (imageType == null) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "只接受 JPEG、PNG 或 WebP 格式的圖片");
            }

            String fileName = UUID.randomUUID() + "." + imageType.extension();
            Path target = uploadDirectory.resolve(fileName).normalize();
            if (!target.startsWith(uploadDirectory)) {
                // 檔名由 UUID 產生，正常情況不可能走到這裡；留著是為了讓假設變成被檢查的條件
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "無效的檔案名稱");
            }

            // header 已從串流讀走，需連同剩餘內容一起寫入
            try (InputStream full = new java.io.SequenceInputStream(
                    new java.io.ByteArrayInputStream(header), content)) {
                Files.copy(full, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return publicBasePath + "/" + fileName;
        } catch (IOException ex) {
            throw new IllegalStateException("圖片寫入失敗", ex);
        }
    }
}
