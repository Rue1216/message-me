package com.esun.social.data.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;

/**
 * 產生資料層用的 {@link SimpleJdbcCall}，統一關閉 JDBC 的程序中繼資料探測。
 *
 * <p><strong>為什麼要關掉中繼資料探測</strong><br>
 * {@code SimpleJdbcCall} 預設會先問資料庫「這支程序有哪些參數」，而
 * MySQL 8 的這條路徑需要 {@code SHOW CREATE PROCEDURE} 或讀取
 * {@code information_schema} 的權限。本專案的 {@code app_user} 依最小權限原則
 * 只有 {@code EXECUTE}（見 {@code DB/01_DDL_schema.sql}），探測必然失敗。
 *
 * <p>因此所有呼叫端都必須自行 {@code declareParameters(...)}。這不只是為了繞過權限問題——
 * 明確宣告讓參數順序與型別成為程式碼的一部分，可在編譯期與測試中被檢查，
 * 也省去每次呼叫的中繼資料往返。
 *
 * <p>呼叫端應在建構時建立一次 {@code SimpleJdbcCall} 並重複使用：
 * 它在首次執行後即編譯完成且為執行緒安全，每次請求重建只是白費工。
 */
public class StoredProcedureCallFactory {

    private final JdbcTemplate jdbcTemplate;

    public StoredProcedureCallFactory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param procedureName Stored Procedure 名稱。一律由程式碼寫死，不接受外部輸入
     * @return 尚未宣告參數的呼叫物件，呼叫端需接著 {@code declareParameters(...)}
     */
    public SimpleJdbcCall forProcedure(String procedureName) {
        return new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName(procedureName)
                .withoutProcedureColumnMetaDataAccess();
    }
}
