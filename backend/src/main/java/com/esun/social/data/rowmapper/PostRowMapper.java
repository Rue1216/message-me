package com.esun.social.data.rowmapper;

import com.esun.social.business.model.Post;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.RowMapper;

/** 將 {@code sp_post_*} 回傳的列轉為 {@link Post}。 */
public class PostRowMapper implements RowMapper<Post> {

    @Override
    public Post mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Post(
                rs.getLong("post_id"),
                rs.getLong("user_id"),
                rs.getString("content"),
                rs.getString("image"),
                rs.getInt("comment_count"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class),
                rs.getString("author_name"),
                rs.getString("author_cover_image"));
    }
}
