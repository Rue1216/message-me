package com.esun.social.common.util;

import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 驗證並正規化發文的標籤。
 *
 * <p><strong>標籤為什麼不從內文解析</strong><br>
 * 標籤與內文是兩種不同的東西：內文是說給人看的句子，標籤是拿來分類與檢索的資料。
 * 過去把兩者塞在同一個欄位裡（內文寫 {@code #登山}，再由後端解析出來），
 * 結果是同一段文字在畫面上出現兩次——內文一次、標籤列一次。
 * 改由使用者在專屬欄位指定之後，內文裡的 {@code #} 就只是一個字元。
 *
 * <p><strong>字元集的選擇</strong><br>
 * 標籤只接受文字、數字與底線（{@code \p{L}\p{N}_}）。{@code \p{L}} 涵蓋中日韓字元，
 * 因此 {@code 登山} 與 {@code vue3} 都成立。這個限制同時帶來兩個好處：
 * 標籤不可能含有逗號，於是資料層能安全地用 {@code GROUP_CONCAT} 把它們攤平成一欄；
 * 也不可能含有引號或反斜線，JSON 序列化不會出現跳脫上的意外。
 *
 * <p><strong>為什麼不經 HtmlSanitizer</strong><br>
 * 專案其他寫入路徑一律以 Jsoup 清洗，標籤是刻意的例外：上面的字元集本身就排除了
 * {@code <}、{@code >}、{@code &} 與所有引號，比 {@code Safelist.none()} 更嚴格。
 * 先驗證再入庫，清洗無事可做。
 *
 * <p><strong>數量上限的界線</strong><br>
 * 上限對「傳入的原始清單」與「去重後的清單」同時成立：送 11 個項目即使其中兩個重複，
 * 仍然拒絕。前端在送出前已經去重，原始數量超過上限只會來自手工構造的請求。
 * 這條規則只在這裡把關——{@code PostRequest} 刻意不放 {@code @Size}，
 * 否則 Bean Validation 會搶先觸發並冠上欄位名，同一件事就有了兩種說法。
 */
@Component
public class TagNormalizer {

    /** 單則發文的標籤數上限。防止有人以整篇標籤灌爆關聯表與交易時間。 */
    public static final int MAX_TAGS_PER_POST = 10;

    /** 與 {@code tags.name} 的 VARCHAR(50) 一致。 */
    public static final int MAX_TAG_LENGTH = 50;

    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N}_]+$");

    /**
     * @param raw 使用者指定的標籤名稱，可為 {@code null}；空字串與 {@code null} 項目會被跳過
     * @return 正規化為小寫、去除重複並保留出現順序的標籤名稱（不含 {@code #}）
     * @throws BusinessException 數量超過上限、單一標籤過長，或含有不合法字元
     */
    public List<String> normalise(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_TAGS_PER_POST) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "標籤最多 " + MAX_TAGS_PER_POST + " 個");
        }

        // LinkedHashSet：同時做到去重與「保留第一次出現的順序」，
        // 讓同一次輸入每次都得到相同的結果，測試才有確定性
        Set<String> normalised = new LinkedHashSet<>();
        for (String candidate : raw) {
            if (candidate == null || candidate.isBlank()) {
                // 空項目不帶任何意圖，跳過而非報錯
                continue;
            }
            // Locale.ROOT：避免土耳其語地區設定把 I 轉成無點的 ı，
            // 造成同一個標籤在不同伺服器上正規化出不同結果
            String tag = candidate.trim().toLowerCase(Locale.ROOT);

            // 長度與字元集分開檢查：合寫成一條 {1,50} 的正規表達式，
            // 「太長」與「有怪字元」就會回同一句訊息，使用者不知道該改哪裡
            if (tag.length() > MAX_TAG_LENGTH) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "標籤不可超過 " + MAX_TAG_LENGTH + " 字");
            }
            if (!ALLOWED.matcher(tag).matches()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "標籤只能使用文字、數字與底線");
            }
            normalised.add(tag);
        }
        return List.copyOf(normalised);
    }
}
