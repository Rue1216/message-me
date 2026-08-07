package com.esun.social.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esun.social.common.config.UploadProperties;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ImageStorageServiceTest {

    private static final long MAX_SIZE = 1024L * 1024L;

    @TempDir
    private Path uploadDirectory;

    private ImageStorageService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new ImageStorageService(
                new UploadProperties(uploadDirectory.toString(), "/uploads", MAX_SIZE));
    }

    @Test
    @DisplayName("JPEG 存檔後路徑可用，內容一位元組不差")
    void storesJpegIntact() throws Exception {
        byte[] content = imageBytes(0xFF, 0xD8, 0xFF);

        String url = service.store(new MockMultipartFile("file", "photo.jpg", "image/jpeg", content));

        assertThat(url).matches("^/uploads/[0-9a-f-]{36}\\.jpg$");
        assertThat(Files.readAllBytes(fileFor(url))).isEqualTo(content);
    }

    @Test
    @DisplayName("PNG 與 WebP 也被接受，副檔名依實際格式決定")
    void storesPngAndWebp() {
        String png = service.store(
                new MockMultipartFile("file", "a.bin", null, imageBytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)));
        String webp = service.store(new MockMultipartFile("file", "b.bin", null, webpBytes()));

        assertThat(png).endsWith(".png");
        assertThat(webp).endsWith(".webp");
    }

    @Test
    @DisplayName("副檔名與 Content-Type 都不算數，只看檔案內容")
    void rejectsNonImageDisguisedAsJpeg() {
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "cat.jpg", "image/jpeg", "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.store(disguised))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::errorCode)
                .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);

        assertThat(uploadDirectory.toFile().list()).isEmpty();
    }

    @Test
    @DisplayName("檔名一律換成 UUID，原始檔名中的路徑穿越無從發揮")
    void ignoresSuppliedFileNameEntirely() {
        String url = service.store(
                new MockMultipartFile("file", "../../../etc/passwd.jpg", "image/jpeg", imageBytes(0xFF, 0xD8, 0xFF)));

        assertThat(url).doesNotContain("..").doesNotContain("passwd");
        assertThat(fileFor(url).getParent()).isEqualTo(uploadDirectory);
    }

    @Test
    @DisplayName("同樣內容連傳兩次會得到兩個不同的檔名，不會互相覆蓋")
    void generatesDistinctNamesForIdenticalContent() {
        byte[] content = imageBytes(0xFF, 0xD8, 0xFF);

        String first = service.store(new MockMultipartFile("file", "a.jpg", "image/jpeg", content));
        String second = service.store(new MockMultipartFile("file", "a.jpg", "image/jpeg", content));

        assertThat(first).isNotEqualTo(second);
        assertThat(uploadDirectory.toFile().list()).hasSize(2);
    }

    @Test
    @DisplayName("空檔案被拒絕")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.store(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::errorCode)
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("超過大小上限的檔案被拒絕")
    void rejectsOversizedFile() {
        byte[] tooBig = new byte[(int) MAX_SIZE + 1];
        tooBig[0] = (byte) 0xFF;
        tooBig[1] = (byte) 0xD8;
        tooBig[2] = (byte) 0xFF;

        assertThatThrownBy(() -> service.store(new MockMultipartFile("file", "big.jpg", "image/jpeg", tooBig)))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::errorCode)
                .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("內容太短而無法判斷格式時視為不支援")
    void rejectsTruncatedContent() {
        assertThatThrownBy(() ->
                        service.store(new MockMultipartFile("file", "tiny.jpg", "image/jpeg", new byte[] {(byte) 0xFF})))
                .isInstanceOf(BusinessException.class);
    }

    private Path fileFor(String url) {
        return uploadDirectory.resolve(url.substring("/uploads/".length()));
    }

    /** 前置的特徵位元組加上足夠長度的內容。 */
    private static byte[] imageBytes(int... magic) {
        byte[] content = new byte[64];
        for (int i = 0; i < magic.length; i++) {
            content[i] = (byte) magic[i];
        }
        for (int i = magic.length; i < content.length; i++) {
            content[i] = (byte) i;
        }
        return content;
    }

    private static byte[] webpBytes() {
        byte[] content = imageBytes(0x52, 0x49, 0x46, 0x46);
        content[8] = 'W';
        content[9] = 'E';
        content[10] = 'B';
        content[11] = 'P';
        return content;
    }
}
