package com.esun.social.data.repository;

import com.esun.social.business.model.Comment;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.data.rowmapper.CommentRowMapper;
import com.esun.social.data.support.StoredProcedureCallFactory;
import com.esun.social.data.support.StoredProcedureErrors;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

/**
 * 留言資料存取，全部經由 {@code sp_comment_*} Stored Procedure。
 *
 * <p>新增與刪除都會連動 {@code posts.comment_count}，兩張表的異動包在 SP 自己的交易裡；
 * 這一層只負責把參數送進去、把錯誤翻成業務例外。
 */
@Repository
public class CommentRepository {

    private static final String RESULT_SET_KEY = "comments";

    private final SimpleJdbcCall createCall;
    private final SimpleJdbcCall listByPostCall;
    private final SimpleJdbcCall countByPostCall;
    private final SimpleJdbcCall deleteCall;

    public CommentRepository(StoredProcedureCallFactory callFactory) {
        this.createCall = callFactory
                .forProcedure("sp_comment_create")
                .declareParameters(
                        new SqlParameter("p_post_id", Types.BIGINT),
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlParameter("p_content", Types.LONGVARCHAR),
                        new SqlOutParameter("p_comment_id", Types.BIGINT));

        this.listByPostCall = callFactory
                .forProcedure("sp_comment_list_by_post")
                .declareParameters(
                        new SqlParameter("p_post_id", Types.BIGINT),
                        new SqlParameter("p_limit", Types.INTEGER),
                        new SqlParameter("p_offset", Types.INTEGER))
                .returningResultSet(RESULT_SET_KEY, new CommentRowMapper());

        this.countByPostCall = callFactory
                .forProcedure("sp_comment_count_by_post")
                .declareParameters(
                        new SqlParameter("p_post_id", Types.BIGINT), new SqlOutParameter("p_total", Types.BIGINT));

        this.deleteCall = callFactory
                .forProcedure("sp_comment_delete")
                .declareParameters(
                        new SqlParameter("p_comment_id", Types.BIGINT),
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlOutParameter("p_affected_rows", Types.INTEGER));
    }

    /**
     * 新增留言並遞增發文的留言數（在 SP 的交易內一併完成）。
     *
     * @return 新留言的 ID
     * @throws BusinessException 發文不存在（由外鍵約束攔下）
     */
    public long create(long postId, long userId, String content) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_post_id", postId)
                .addValue("p_user_id", userId)
                .addValue("p_content", content);
        try {
            return ((Number) createCall.execute(parameters).get("p_comment_id")).longValue();
        } catch (DataAccessException ex) {
            if (StoredProcedureErrors.isMySqlError(ex, StoredProcedureErrors.FOREIGN_KEY_VIOLATION)) {
                // 業務層檢查與寫入之間發文被刪掉；SP 已回滾，留言數不會失準
                throw new BusinessException(ErrorCode.NOT_FOUND, "找不到這篇發文", ex);
            }
            throw ex;
        }
    }

    /** 單篇發文的留言分頁，舊到新。 */
    @SuppressWarnings("unchecked")
    public List<Comment> findPageByPost(long postId, int limit, int offset) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_post_id", postId)
                .addValue("p_limit", limit)
                .addValue("p_offset", offset);
        List<Comment> comments = (List<Comment>) listByPostCall.execute(parameters).get(RESULT_SET_KEY);
        return comments == null ? List.of() : comments;
    }

    public long countByPost(long postId) {
        Map<String, Object> result = countByPostCall.execute(new MapSqlParameterSource("p_post_id", postId));
        return ((Number) result.get("p_total")).longValue();
    }

    /**
     * 刪除留言並遞減發文的留言數，僅限留言者本人（SP 內以 {@code user_id} 比對）。
     *
     * @return 是否確實刪除；留言不存在或不屬於此使用者時為 {@code false}
     */
    public boolean delete(long commentId, long userId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_comment_id", commentId)
                .addValue("p_user_id", userId);
        Map<String, Object> result = deleteCall.execute(parameters);
        return ((Number) result.get("p_affected_rows")).intValue() > 0;
    }
}
