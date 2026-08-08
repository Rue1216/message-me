package com.esun.social.data.rowmapper;

import com.esun.social.business.model.Post;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;

/**
 * 將 {@code v_post_detail} 的列轉為 {@link Post}。
 *
 * <p>四支發文查詢程序（列表、單篇、搜尋、依標籤）共用同一份投影，因此也共用這一個對應器。
 */
public class PostRowMapper implements RowMapper<Post> {

    private static final String TAG_SEPARATOR = ",";

    @Override
    public Post mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Post(
                rs.getLong("post_id"),
                rs.getLong("user_id"),
                rs.getString("content"),
                rs.getString("image"),
                rs.getInt("comment_count"),
                rs.getInt("like_count"),
                rs.getBoolean("liked_by_me"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class),
                rs.getString("author_name"),
                rs.getString("author_cover_image"),
                rs.getObject("author_deleted_at", LocalDateTime.class) != null,
                splitTags(rs.getString("tag_names")));
    }

    /**
     * 還原 GROUP_CONCAT 攤平的標籤字串。
     *
     * <p>標籤名稱在寫入前已由 {@code TagNormalizer} 限縮為 {@code [\p{L}\p{N}_]}，
     * 不可能含有分隔符，因此切分不會產生歧義。沒有任何標籤時 SQL 回傳的是 NULL 而非空字串。
     */
    private static List<String> splitTags(String concatenated) {
        if (concatenated == null || concatenated.isBlank()) {
            return List.of();
        }
        return Arrays.asList(concatenated.split(TAG_SEPARATOR));
    }
}
