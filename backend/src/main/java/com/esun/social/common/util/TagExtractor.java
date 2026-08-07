package com.esun.social.common.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 自發文內容中解析 {@code #標籤}。
 *
 * <p><strong>為什麼解析在這裡而不在 SQL 裡</strong><br>
 * 規格要求的是「透過 Stored Procedure 存取資料庫」，而不是「用 SQL 做字串處理」。
 * 在 SP 內以 {@code WHILE} 搭配 {@code SUBSTRING_INDEX} 手工切字串，換來的是一段難讀且
 * 難以單獨測試的迴圈；解析留在業務層則是一個純函式，可以直接對它寫測試。
 * 寫入資料庫時仍是單一 SP 呼叫，跨表交易的完整性不受影響。
 *
 * <p><strong>字元集的選擇</strong><br>
 * 標籤只接受文字、數字與底線（{@code \p{L}\p{N}_}）。{@code \p{L}} 涵蓋中日韓字元，
 * 因此 {@code #登山} 與 {@code #vue3} 都成立。這個限制同時帶來兩個好處：
 * 標籤不可能含有逗號，於是資料層能安全地用 {@code GROUP_CONCAT} 把它們攤平成一欄；
 * 也不可能含有引號或反斜線，JSON 序列化不會出現跳脫上的意外。
 *
 * <p>前置的否定回顧 {@code (?<![\p{L}\p{N}_])} 要求 {@code #} 前面不是文字字元，
 * 使 {@code C#} 或 {@code issue#42} 這類寫法不會被誤判為標籤。
 */
@Component
public class TagExtractor {

    /** 單則發文的標籤數上限。防止有人以整篇標籤灌爆關聯表與交易時間。 */
    public static final int MAX_TAGS_PER_POST = 10;

    /** 與 {@code tags.name} 的 VARCHAR(50) 一致。 */
    public static final int MAX_TAG_LENGTH = 50;

    private static final Pattern HASHTAG =
            Pattern.compile("(?<![\\p{L}\\p{N}_])#([\\p{L}\\p{N}_]{1," + MAX_TAG_LENGTH + "})");

    /**
     * @param content 已清洗的發文內容，可為 {@code null}
     * @return 正規化為小寫、去除重複並保留出現順序的標籤名稱（不含 {@code #}），
     *         最多 {@value #MAX_TAGS_PER_POST} 個；沒有標籤時回傳空清單
     */
    public List<String> extract(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // LinkedHashSet：同時做到去重與「保留第一次出現的順序」，
        // 讓同一篇發文每次解析出的結果都相同，測試才有確定性
        Set<String> tags = new LinkedHashSet<>();
        Matcher matcher = HASHTAG.matcher(content);
        while (matcher.find() && tags.size() < MAX_TAGS_PER_POST) {
            // Locale.ROOT：避免土耳其語地區設定把 I 轉成無點的 ı，
            // 造成同一個標籤在不同伺服器上正規化出不同結果
            tags.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return List.copyOf(tags);
    }
}
