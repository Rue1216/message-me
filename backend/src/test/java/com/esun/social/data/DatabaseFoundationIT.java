package com.esun.social.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esun.social.data.support.StoredProcedureCallFactory;
import com.esun.social.support.MySqlContainerSupport;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;

/**
 * 資料層基礎設施的整合測試 —— 在真實的 MySQL 上驗證三件事：
 * 應用程式能連上資料庫、Stored Procedure 呼叫路徑可用、最小權限確實生效。
 */
class DatabaseFoundationIT extends MySqlContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StoredProcedureCallFactory callFactory;

    @Test
    @DisplayName("應用程式以 app_user 連線，資料庫為 message_me")
    void connectsAsLeastPrivilegedUser() {
        assertThat(jdbcTemplate.queryForObject("SELECT CURRENT_USER()", String.class)).startsWith("app_user@");
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("message_me");
    }

    @Test
    @DisplayName("即使繞過應用程式，app_user 也無法直接讀取資料表")
    void cannotQueryTablesDirectly() {
        assertThatThrownBy(() -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("SELECT command denied");
    }

    @Test
    @DisplayName("只有 EXECUTE 權限時仍能呼叫 Stored Procedure 並取得 OUT 參數")
    void callsProcedureWithOutParameter() {
        SimpleJdbcCall call = callFactory
                .forProcedure("sp_comment_count_by_post")
                .declareParameters(
                        new SqlParameter("p_post_id", Types.BIGINT), new SqlOutParameter("p_total", Types.BIGINT));

        // 03_DML_seed_data.sql 的第 1 篇發文有兩則示範留言
        Map<String, Object> result = call.execute(Map.of("p_post_id", 1L));

        assertThat((Long) result.get("p_total")).isGreaterThanOrEqualTo(2L);
    }

    @Test
    @DisplayName("Stored Procedure 回傳的結果集可經 RowMapper 取出，分頁參數以繫結傳入")
    void callsProcedureReturningResultSet() {
        Map<String, Object> result = listCall((rs, rowNum) ->
                        Map.of("postId", rs.getLong("post_id"), "authorName", rs.getString("author_name")))
                .execute(cursorParameters(2));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> posts = (List<Map<String, Object>>) result.get("posts");
        assertThat(posts).hasSize(2);
        // 檢視表 JOIN users 帶出的作者資訊確實有被對應到
        assertThat(posts).allSatisfy(post -> {
            assertThat((Long) post.get("postId")).isPositive();
            assertThat((String) post.get("authorName")).isNotBlank();
        });
    }

    @Test
    @DisplayName("SP 內的 LIMIT 夾限生效：要求超量時最多回 100 筆")
    void clampsPageSizeInsideProcedure() {
        Map<String, Object> result =
                listCall((rs, rowNum) -> rs.getLong("post_id")).execute(cursorParameters(9999));

        @SuppressWarnings("unchecked")
        List<Long> postIds = (List<Long>) result.get("posts");
        assertThat(postIds).hasSizeLessThanOrEqualTo(100);
    }

    /** {@code sp_post_list_cursor} 的呼叫樣板，兩個測試共用。 */
    private SimpleJdbcCall listCall(RowMapper<?> rowMapper) {
        return callFactory
                .forProcedure("sp_post_list_cursor")
                .declareParameters(
                        new SqlParameter("p_viewer_id", Types.BIGINT),
                        new SqlParameter("p_cursor_created_at", Types.TIMESTAMP),
                        new SqlParameter("p_cursor_post_id", Types.BIGINT),
                        new SqlParameter("p_limit", Types.INTEGER))
                .returningResultSet("posts", rowMapper);
    }

    /** 第一頁（沒有游標）、訪客身分。HashMap 而非 Map.of：後者不接受 null 值。 */
    private static Map<String, Object> cursorParameters(int limit) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("p_viewer_id", null);
        parameters.put("p_cursor_created_at", null);
        parameters.put("p_cursor_post_id", null);
        parameters.put("p_limit", limit);
        return parameters;
    }
}
