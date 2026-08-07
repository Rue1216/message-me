package com.esun.social.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TagExtractorTest {

    private final TagExtractor extractor = new TagExtractor();

    @Test
    @DisplayName("解析出內容中的標籤，不含 # 字元")
    void extractsHashtags() {
        assertThat(extractor.extract("今天去 #登山 順便吃 #美食")).containsExactly("登山", "美食");
    }

    @Test
    @DisplayName("中日韓字元可作為標籤")
    void supportsCjkCharacters() {
        assertThat(extractor.extract("#陽明山 真美")).containsExactly("陽明山");
    }

    @Test
    @DisplayName("英數與底線可作為標籤")
    void supportsAlphanumericAndUnderscore() {
        assertThat(extractor.extract("#vue3 #spring_boot")).containsExactly("vue3", "spring_boot");
    }

    @Test
    @DisplayName("正規化為小寫，使 #Vue 與 #vue 落在同一個標籤上")
    void normalisesToLowerCase() {
        assertThat(extractor.extract("#Vue #VUE #vue")).containsExactly("vue");
    }

    @Test
    @DisplayName("去除重複但保留第一次出現的順序")
    void deduplicatesPreservingOrder() {
        assertThat(extractor.extract("#b #a #b #c")).containsExactly("b", "a", "c");
    }

    @ParameterizedTest
    @ValueSource(strings = {"C#", "issue#42", "abc#def"})
    @DisplayName("# 前面是文字字元時不視為標籤")
    void ignoresHashPrecededByWordCharacter(String content) {
        assertThat(extractor.extract(content)).isEmpty();
    }

    @Test
    @DisplayName("單獨的 # 或後面沒有合法字元時不產生標籤")
    void ignoresBareHash() {
        assertThat(extractor.extract("# 空的 #!!! #")).isEmpty();
    }

    @Test
    @DisplayName("標籤數量以上限為止，避免有人用整篇標籤灌爆關聯表")
    void capsNumberOfTags() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < TagExtractor.MAX_TAGS_PER_POST + 5; i++) {
            content.append(" #tag").append(i);
        }

        assertThat(extractor.extract(content.toString())).hasSize(TagExtractor.MAX_TAGS_PER_POST);
    }

    @Test
    @DisplayName("超過長度上限的標籤只取到上限為止，不會超出 tags.name 的欄位長度")
    void truncatesOverlongTagAtColumnLimit() {
        String overlong = "#" + "a".repeat(TagExtractor.MAX_TAG_LENGTH + 10);

        List<String> tags = extractor.extract(overlong);

        assertThat(tags).hasSize(1);
        assertThat(tags.get(0)).hasSize(TagExtractor.MAX_TAG_LENGTH);
    }

    @Test
    @DisplayName("空白或 null 的內容回傳空清單")
    void handlesEmptyInput() {
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("   ")).isEmpty();
    }

    @Test
    @DisplayName("沒有標籤的一般內容回傳空清單")
    void returnsEmptyWhenNoHashtags() {
        assertThat(extractor.extract("今天天氣真好")).isEmpty();
    }
}
