package com.esun.social.data.repository;

import com.esun.social.business.model.Post;
import com.esun.social.data.rowmapper.PostRowMapper;
import com.esun.social.data.support.StoredProcedureCallFactory;
import java.sql.Types;
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
    private final SimpleJdbcCall listCall;
    private final SimpleJdbcCall countCall;
    private final SimpleJdbcCall findByIdCall;
    private final SimpleJdbcCall updateCall;
    private final SimpleJdbcCall deleteCall;

    public PostRepository(StoredProcedureCallFactory callFactory) {
        this.createCall = callFactory
                .forProcedure("sp_post_create")
                .declareParameters(
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlParameter("p_content", Types.LONGVARCHAR),
                        new SqlParameter("p_image", Types.VARCHAR),
                        new SqlOutParameter("p_post_id", Types.BIGINT));

        this.listCall = callFactory
                .forProcedure("sp_post_list")
                .declareParameters(
                        new SqlParameter("p_limit", Types.INTEGER), new SqlParameter("p_offset", Types.INTEGER))
                .returningResultSet(RESULT_SET_KEY, new PostRowMapper());

        this.countCall = callFactory
                .forProcedure("sp_post_count")
                .declareParameters(new SqlOutParameter("p_total", Types.BIGINT));

        this.findByIdCall = callFactory
                .forProcedure("sp_post_find_by_id")
                .declareParameters(new SqlParameter("p_post_id", Types.BIGINT))
                .returningResultSet(RESULT_SET_KEY, new PostRowMapper());

        this.updateCall = callFactory
                .forProcedure("sp_post_update")
                .declareParameters(
                        new SqlParameter("p_post_id", Types.BIGINT),
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlParameter("p_content", Types.LONGVARCHAR),
                        new SqlParameter("p_image", Types.VARCHAR),
                        new SqlOutParameter("p_affected_rows", Types.INTEGER));

        this.deleteCall = callFactory
                .forProcedure("sp_post_delete")
                .declareParameters(
                        new SqlParameter("p_post_id", Types.BIGINT),
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlOutParameter("p_affected_rows", Types.INTEGER));
    }

    /** @return 新發文的 ID */
    public long create(long userId, String content, String image) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_user_id", userId)
                .addValue("p_content", content)
                .addValue("p_image", image);
        return ((Number) createCall.execute(parameters).get("p_post_id")).longValue();
    }

    /** 時間軸分頁，新到舊。{@code limit} 由 SP 夾限在 1..100。 */
    @SuppressWarnings("unchecked")
    public List<Post> findPage(int limit, int offset) {
        Map<String, Object> result = listCall.execute(
                new MapSqlParameterSource().addValue("p_limit", limit).addValue("p_offset", offset));
        List<Post> posts = (List<Post>) result.get(RESULT_SET_KEY);
        return posts == null ? List.of() : posts;
    }

    public long count() {
        return ((Number) countCall.execute(new MapSqlParameterSource()).get("p_total")).longValue();
    }

    @SuppressWarnings("unchecked")
    public Optional<Post> findById(long postId) {
        Map<String, Object> result = findByIdCall.execute(new MapSqlParameterSource("p_post_id", postId));
        List<Post> posts = (List<Post>) result.get(RESULT_SET_KEY);
        return posts == null || posts.isEmpty() ? Optional.empty() : Optional.of(posts.get(0));
    }

    /**
     * 編輯發文。SP 內以 {@code WHERE post_id = ? AND user_id = ?} 比對，
     * 因此非本人的編輯必然影響 0 列——授權在資料庫層再確認一次。
     *
     * @return 是否確實更新
     */
    public boolean update(long postId, long userId, String content, String image) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_post_id", postId)
                .addValue("p_user_id", userId)
                .addValue("p_content", content)
                .addValue("p_image", image);
        return affectedRows(updateCall.execute(parameters)) > 0;
    }

    /**
     * 刪除發文與其全部留言。跨表異動與回滾都在 {@code sp_post_delete} 的交易內完成。
     *
     * @return 是否確實刪除
     */
    public boolean delete(long postId, long userId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_post_id", postId)
                .addValue("p_user_id", userId);
        return affectedRows(deleteCall.execute(parameters)) > 0;
    }

    private int affectedRows(Map<String, Object> result) {
        return ((Number) result.get("p_affected_rows")).intValue();
    }
}
