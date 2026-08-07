package com.esun.social.common.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 使用者輸入的文字清洗 —— XSS 防護的輸入端。
 *
 * <p>本平台的發文、留言、自介與使用者名稱都只是純文字，沒有任何需要保留 HTML 的情境，
 * 因此採用 {@link Safelist#none()}：<strong>不是過濾危險標籤，而是一個都不留。</strong>
 * 白名單式的防護不需要追著新的繞過手法跑。
 *
 * <p>清洗後再以 {@link Parser#unescapeEntities} 還原 HTML 實體，讓資料庫存的是使用者
 * 真正輸入的字元。輸出端的轉義由 Vue 的 {@code {{ }}} 負責（專案禁用 {@code v-html}），
 * 若這裡也存成 {@code &amp;amp;}，使用者打的 {@code &} 會在畫面上變成 {@code &amp;}。
 *
 * <p>兩端各司其職：輸入端移除結構、輸出端負責轉義。
 */
@Component
public class HtmlSanitizer {

    private static final Safelist NOTHING_ALLOWED = Safelist.none();

    /** prettyPrint 會重排空白並吃掉換行，多行發文因此需要關掉它。 */
    private static final Document.OutputSettings PRESERVE_WHITESPACE =
            new Document.OutputSettings().prettyPrint(false);

    /**
     * @param input 使用者輸入，可為 {@code null}
     * @return 去除全部標籤並修剪前後空白的純文字；輸入為 {@code null} 時回傳 {@code null}
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }
        String withoutMarkup = Jsoup.clean(input, "", NOTHING_ALLOWED, PRESERVE_WHITESPACE);
        return Parser.unescapeEntities(withoutMarkup, false).trim();
    }
}
