package com.esun.social.data.rowmapper;

import com.esun.social.business.model.Activity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.RowMapper;

/** 將 {@code sp_user_activity_list} 回傳的列轉為 {@link Activity}。 */
public class ActivityRowMapper implements RowMapper<Activity> {

    @Override
    public Activity mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Activity(
                Activity.ActivityType.valueOf(rs.getString("activity_type")),
                rs.getLong("activity_id"),
                rs.getLong("post_id"),
                rs.getString("content"),
                rs.getString("image"),
                rs.getInt("comment_count"),
                rs.getInt("like_count"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getString("post_excerpt"),
                rs.getString("post_author_name"));
    }
}
