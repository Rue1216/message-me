package com.esun.social.common.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageResponseTest {

    @ParameterizedTest(name = "共 {0} 筆、每頁 {1} 筆 → {2} 頁")
    @CsvSource({
        "0, 10, 0",
        "1, 10, 1",
        "10, 10, 1",
        "11, 10, 2",
        "25, 10, 3",
    })
    @DisplayName("總頁數以無條件進位計算，零筆資料為零頁")
    void calculatesTotalPages(long totalElements, int size, int expectedTotalPages) {
        PageResponse<String> page = PageResponse.of(List.of(), 1, size, totalElements);

        assertThat(page.totalPages()).isEqualTo(expectedTotalPages);
    }

    @Test
    @DisplayName("保留呼叫端傳入的內容與分頁參數")
    void keepsItemsAndPagingParameters() {
        PageResponse<String> page = PageResponse.of(List.of("a", "b"), 2, 2, 5);

        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("items 不可被外部修改")
    void exposesImmutableItems() {
        List<String> source = new java.util.ArrayList<>(List.of("a"));
        PageResponse<String> page = PageResponse.of(source, 1, 10, 1);

        source.add("b");

        assertThat(page.items()).containsExactly("a");
        assertThatThrownBy(() -> page.items().add("c")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("map 只轉換內容，分頁資訊原樣保留")
    void mapsItemsKeepingPagingInformation() {
        PageResponse<Integer> lengths = PageResponse.of(List.of("abc", "de"), 2, 2, 5).map(String::length);

        assertThat(lengths.items()).containsExactly(3, 2);
        assertThat(lengths.page()).isEqualTo(2);
        assertThat(lengths.size()).isEqualTo(2);
        assertThat(lengths.totalElements()).isEqualTo(5);
        assertThat(lengths.totalPages()).isEqualTo(3);
    }

    @ParameterizedTest(name = "page={0}, size={1} 應被拒絕")
    @CsvSource({"0, 10", "-1, 10", "1, 0", "1, -5"})
    @DisplayName("頁碼自 1 起算，頁碼與每頁筆數皆須為正整數")
    void rejectsNonPositivePaging(int page, int size) {
        assertThatThrownBy(() -> PageResponse.of(List.of(), page, size, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
