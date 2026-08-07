package com.esun.social.data.repository;

import com.esun.social.business.model.Post;
import com.esun.social.common.util.Cursor;
import com.esun.social.data.rowmapper.PostRowMapper;
import com.esun.social.data.support.StoredProcedureCallFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

/** 發文資料存取，全部經由 {@code sp_post_*} Stored Procedure。 */
@Repository
public class PostRepository {

    private static final String RESULT_SET_KEY = "posts";

    private final SimpleJdbcCall createCall;
    private final SimpleJdbcCall listCursorCall;
    private final SimpleJdbcCall searchCall;
    private final SimpleJdbcCall listByTagCall;
    private final SimpleJdbcCall findByIdCall;
    private final SimpleJdbcCall updateCall;
    private final SimpleJdbcCall deleteCall;
    private final SimpleJdbcCall likeCall;
    private final SimpleJdbcCall unlikeCall;
    private final ObjectMapper objectMapper;

    public PostRepository(StoredProcedureCallFactory callFactory, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        this.createCall = callFactory
                .forProcedure("sp_post_create")
                .declareParameters(
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlParameter("p_content", Types.LONGVARCHAR),
                        new SqlParameter("p_image", Types.VARCHAR),
                        new SqlParameter("p_tags_json", Types.LONGVARCHAR),
                        new SqlOutParameter("p_post_id", Types.BIGINT));

        this.listCursorCall = callFactory
                .forProcedure("sp_post_list_cursor")
                .declareParameters(
                        new SqlParameter("p_viewer_id", Types.BIGINT),
                        new SqlParameter("p_cursor_created_at", Types.TIMESTAMP),
                        new SqlParameter("p_cursor_post_id", Types.BIGINT),
                        new SqlParameter("p_limit", Types.INTEGER))
                .returningResultSet(RESULT_SET_KEY, new PostRowMapper());

        this.searchCall = callFactory
                .forProcedure("sp_post_search")
                .declareParameters(
                        new SqlParameter("p_viewer_id", Types.BIGINT),
                        new SqlParameter("p_keyword", Types.VARCHAR),
                        new SqlParameter("p_cursor_created_at", Types.TIMESTAMP),
                        new SqlParameter("p_cursor_post_id", Types.BIGINT),
                        new SqlParameter("p_limit", Types.INTEGER))
                .returningResultSet(RESULT_SET_KEY, new PostRowMapper());

        this.listByTagCall = callFactory
                .forProcedure("sp_post_list_by_tag")
                .declareParameters(
                        new SqlParameter("p_viewer_id", Types.BIGINT),
                        new SqlParameter("p_tag_name", Types.VARCHAR),
                        new SqlParameter("p_cursor_created_at", Types.TIMESTAMP),
                        new SqlParameter("p_cursor_post_id", Types.BIGINT),
                        new SqlParameter("p_limit", Types.INTEGER))
                .returningResultSet(RESULT_SET_KEY, new PostRowMapper());

        this.findByIdCall = callFactory
                .forProcedure("sp_post_find_by_id")
                .declareParameters(
                        new SqlParameter("p_viewer_id", Types.BIGINT), new SqlParameter("p_post_id", Types.BIGINT))
                .returningResultSet(RESULT_SET_KEY, new PostRowMapper());

        this.updateCall = callFactory
                .forProcedure("sp_post_update")
                .declareParameters(
                        new SqlParameter("p_post_id", Types.BIGINT),
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlParameter("p_content", Types.LONGVARCHAR),
                        new SqlParameter("p_image", Types.VARCHAR),
                        new SqlParameter("p_tags_json", Types.LONGVARCHAR),
                        new SqlOutParameter("p_affected_rows", Types.INTEGER));

        this.deleteCall = callFactory
                .forProcedure("sp_post_delete")
                .declareParameters(
                        new SqlParameter("p_post_id", Types.BIGINT),
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlOutParameter("p_affected_rows", Types.INTEGER));

        this.likeCall = callFactory
                .forProcedure("sp_post_like")
                .declareParameters(
                        new SqlParameter("p_post_id", Types.BIGINT),
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlOutParameter("p_like_count", Types.INTEGER));

        this.unlikeCall = callFactory
                .forProcedure("sp_post_unlike")
                .declareParameters(
                        new SqlParameter("p_post_id", Types.BIGINT),
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlOutParameter("p_like_count", Types.INTEGER));
    }

    /** @return 新發文的 ID */
    public long create(long userId, String content, String image, List<String> tags) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_user_id", userId)
                .addValue("p_content", content)
                .addValue("p_image", image)
                .addValue("p_tags_json", toJsonArray(tags));
        return ((Number) createCall.execute(parameters).get("p_post_id")).longValue();
    }

    /**
     * 時間軸的 keyset 分頁，新到舊。
     *
     * <p>SP 會回傳最多 {@code limit + 1} 筆，多出來的那一筆用來判斷是否還有下一頁；
     * 裁切與游標的產生由 {@code PostService} 負責，這一層只忠實回傳 SP 的結果。
     *
     * @param viewerId 觀看者，未登入時為 {@code null}
     * @param cursor   上一頁的結尾位置，第一頁時為 {@code null}
     */
    public List<Post> findPageByCursor(Long viewerId, Cursor.Position cursor, int limit) {
        return queryPosts(listCursorCall, cursorParameters(viewerId, cursor, limit));
    }

    /** 關鍵字搜尋，分頁語意同 {@link #findPageByCursor}。 */
    public List<Post> searchByCursor(Long viewerId, String keyword, Cursor.Position cursor, int limit) {
        return queryPosts(searchCall, cursorParameters(viewerId, cursor, limit).addValue("p_keyword", keyword));
    }

    /** 依標籤列出，分頁語意同 {@link #findPageByCursor}。 */
    public List<Post> findPageByTag(Long viewerId, String tagName, Cursor.Position cursor, int limit) {
        return queryPosts(listByTagCall, cursorParameters(viewerId, cursor, limit).addValue("p_tag_name", tagName));
    }

    public Optional<Post> findById(Long viewerId, long postId) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource().addValue("p_viewer_id", viewerId).addValue("p_post_id", postId);
        List<Post> posts = queryPosts(findByIdCall, parameters);
        return posts.isEmpty() ? Optional.empty() : Optional.of(posts.get(0));
    }

    /**
     * 編輯發文並重掛標籤。SP 內以 {@code post_id = ? AND user_id = ?} 比對，
     * 因此非本人的編輯必然影響 0 列——授權在資料庫層再確認一次。
     *
     * @return 是否確實更新
     */
    public boolean update(long postId, long userId, String content, String image, List<String> tags) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_post_id", postId)
                .addValue("p_user_id", userId)
                .addValue("p_content", content)
                .addValue("p_image", image)
                .addValue("p_tags_json", toJsonArray(tags));
        return affectedRows(updateCall.execute(parameters)) > 0;
    }

    /**
     * 刪除發文與其全部留言、按讚與標籤關聯。跨表異動與回滾都在 {@code sp_post_delete} 的交易內完成。
     *
     * @return 是否確實刪除
     */
    public boolean delete(long postId, long userId) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource().addValue("p_post_id", postId).addValue("p_user_id", userId);
        return affectedRows(deleteCall.execute(parameters)) > 0;
    }

    /**
     * 按讚。冪等：重複呼叫不會使計數增加。
     *
     * @return 最新的按讚數；發文不存在時為 {@link Optional#empty()}
     */
    public Optional<Integer> like(long postId, long userId) {
        return likeCount(likeCall, postId, userId);
    }

    /**
     * 取消按讚。冪等：未按過讚時不會使計數減少。
     *
     * @return 最新的按讚數；發文不存在時為 {@link Optional#empty()}
     */
    public Optional<Integer> unlike(long postId, long userId) {
        return likeCount(unlikeCall, postId, userId);
    }

    private Optional<Integer> likeCount(SimpleJdbcCall call, long postId, long userId) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource().addValue("p_post_id", postId).addValue("p_user_id", userId);
        // SP 以 MAX() 取值，查無該發文時回傳 NULL——這是「發文不存在」的唯一訊號，
        // 因為 INSERT IGNORE 會把外鍵違反降級成警告而不拋例外
        Number likeCount = (Number) call.execute(parameters).get("p_like_count");
        return Optional.ofNullable(likeCount).map(Number::intValue);
    }

    @SuppressWarnings("unchecked")
    private List<Post> queryPosts(SimpleJdbcCall call, MapSqlParameterSource parameters) {
        Map<String, Object> result = call.execute(parameters);
        List<Post> posts = (List<Post>) result.get(RESULT_SET_KEY);
        return posts == null ? List.of() : posts;
    }

    private MapSqlParameterSource cursorParameters(Long viewerId, Cursor.Position cursor, int limit) {
        LocalDateTime cursorCreatedAt = cursor == null ? null : cursor.createdAt();
        Long cursorId = cursor == null ? null : cursor.id();
        return new MapSqlParameterSource()
                .addValue("p_viewer_id", viewerId)
                .addValue("p_cursor_created_at", cursorCreatedAt)
                .addValue("p_cursor_post_id", cursorId)
                .addValue("p_limit", limit);
    }

    /**
     * 標籤清單序列化為 JSON 陣列，供 SP 以 {@code JSON_TABLE} 展開。
     *
     * <p>以 Jackson 而非字串拼接：即使標籤字元集已排除引號與反斜線
     * （見 {@code TagExtractor}），手工拼 JSON 也是把正確性押在一個遠處的約定上。
     */
    private String toJsonArray(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
        } catch (JsonProcessingException e) {
            // 輸入是 List<String>，實務上不可能失敗；真的發生代表程式有更根本的問題
            throw new IllegalStateException("標籤序列化失敗", e);
        }
    }

    private int affectedRows(Map<String, Object> result) {
        return ((Number) result.get("p_affected_rows")).intValue();
    }
}
