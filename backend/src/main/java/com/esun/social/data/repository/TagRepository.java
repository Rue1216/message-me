package com.esun.social.data.repository;

import com.esun.social.business.model.Tag;
import com.esun.social.data.rowmapper.TagRowMapper;
import com.esun.social.data.support.StoredProcedureCallFactory;
import java.sql.Types;
import java.util.List;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

/**
 * 標籤資料存取。
 *
 * <p>這裡只有讀取：標籤的建立與計數維護全都發生在 {@code sp_post_create / update / delete}
 * 的交易之內，與發文本身的異動不可分割。若在此另開一支「新增標籤」，就等於提供了一條
 * 繞過該交易的路徑，反正規化的 {@code post_count} 也就失去了唯一的維護者。
 */
@Repository
public class TagRepository {

    private static final String RESULT_SET_KEY = "tags";

    private final SimpleJdbcCall listPopularCall;

    public TagRepository(StoredProcedureCallFactory callFactory) {
        this.listPopularCall = callFactory
                .forProcedure("sp_tag_list_popular")
                .declareParameters(new SqlParameter("p_limit", Types.INTEGER))
                .returningResultSet(RESULT_SET_KEY, new TagRowMapper());
    }

    /** 依使用次數排序的熱門標籤；未被任何發文使用的標籤不會出現。 */
    @SuppressWarnings("unchecked")
    public List<Tag> findPopular(int limit) {
        List<Tag> tags = (List<Tag>) listPopularCall
                .execute(new MapSqlParameterSource("p_limit", limit))
                .get(RESULT_SET_KEY);
        return tags == null ? List.of() : tags;
    }
}
