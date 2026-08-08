package com.esun.social.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TagNormalizerTest {

    private final TagNormalizer normalizer = new TagNormalizer();

    @Test
    @DisplayName("正規化為小寫，使 Vue3 與 vue3 落在同一個標籤上")
    void normalisesToLowerCase() {
        assertThat(normalizer.normalise(List.of("Vue3", "VUE3", "vue3"))).containsExactly("vue3");
    }

    @Test
    @DisplayName("去除重複但保留第一次出現的順序")
    void deduplicatesPreservingOrder() {
        assertThat(normalizer.normalise(List.of("b", "a", "b", "c"))).containsExactly("b", "a", "c");
    }

    @Test
    @DisplayName("去除前後空白")
    void trimsSurroundingWhitespace() {
        assertThat(normalizer.normalise(List.of("  登山  "))).containsExactly("登山");
    }

    @Test
    @DisplayName("空字串與 null 項目跳過，不視為錯誤")
    void skipsBlankEntries() {
        assertThat(normalizer.normalise(Arrays.asList("登山", "", "   ", null))).containsExactly("登山");
    }

    @Test
    @DisplayName("null 或空清單回傳空清單")
    void handlesEmptyInput() {
        assertThat(normalizer.normalise(null)).isEmpty();
        assertThat(normalizer.normalise(List.of())).isEmpty();
    }

    @Test
    @DisplayName("中日韓字元可作為標籤")
    void supportsCjkCharacters() {
        assertThat(normalizer.normalise(List.of("陽明山"))).containsExactly("陽明山");
    }

    @Test
    @DisplayName("英數與底線可作為標籤")
    void supportsAlphanumericAndUnderscore() {
        assertThat(normalizer.normalise(List.of("vue3", "spring_boot")))
                .containsExactly("vue3", "spring_boot");
    }

    @ParameterizedTest
    @ValueSource(strings = {"台北101!", "台 北", "a,b", "#登山", "半形-連字號"})
    @DisplayName("含不合法字元的標籤被拒")
    void rejectsIllegalCharacters(String tag) {
        assertThatThrownBy(() -> normalizer.normalise(List.of(tag)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("標籤只能使用文字、數字與底線")
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("超過長度上限的標籤被拒，不會超出 tags.name 的欄位長度")
    void rejectsOverlongTag() {
        String overlong = "a".repeat(TagNormalizer.MAX_TAG_LENGTH + 1);

        assertThatThrownBy(() -> normalizer.normalise(List.of(overlong)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("標籤不可超過 50 字");
    }

    @Test
    @DisplayName("剛好等於長度上限的標籤通過")
    void acceptsTagAtLengthLimit() {
        String atLimit = "a".repeat(TagNormalizer.MAX_TAG_LENGTH);

        assertThat(normalizer.normalise(List.of(atLimit))).containsExactly(atLimit);
    }

    @Test
    @DisplayName("超過數量上限的清單被拒，避免有人以整篇標籤灌爆關聯表")
    void rejectsMoreThanMaxTags() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= TagNormalizer.MAX_TAGS_PER_POST; i++) {
            tooMany.add("tag" + i);
        }

        assertThatThrownBy(() -> normalizer.normalise(tooMany))
                .isInstanceOf(BusinessException.class)
                .hasMessage("標籤最多 10 個");
    }

    @Test
    @DisplayName("原始數量超過上限時，即使去重後會落在上限內仍然被拒")
    void rejectsOverLimitEvenWhenDuplicatesWouldReduceIt() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < TagNormalizer.MAX_TAGS_PER_POST; i++) {
            tooMany.add("tag" + i);
        }
        tooMany.add("tag0"); // 第 11 個，與第 1 個重複

        assertThatThrownBy(() -> normalizer.normalise(tooMany))
                .isInstanceOf(BusinessException.class)
                .hasMessage("標籤最多 10 個");
    }

    @Test
    @DisplayName("剛好等於數量上限的清單通過")
    void acceptsExactlyMaxTags() {
        List<String> exactly = new ArrayList<>();
        for (int i = 0; i < TagNormalizer.MAX_TAGS_PER_POST; i++) {
            exactly.add("tag" + i);
        }

        assertThat(normalizer.normalise(exactly)).hasSize(TagNormalizer.MAX_TAGS_PER_POST);
    }
}
