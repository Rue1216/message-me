package com.esun.social.data.rowmapper;

import com.esun.social.business.model.Comment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.RowMapper;

/** 將 {@code sp_comment_list_by_post} 回傳的列轉為 {@link Comment}。 */
public class CommentRowMapper implements RowMapper<Comment> {

    @Override
    public Comment mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Comment(
                rs.getLong("comment_id"),
                rs.getLong("post_id"),
                rs.getLong("user_id"),
                rs.getString("content"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getString("author_name"),
                rs.getString("author_cover_image"));
    }
}
