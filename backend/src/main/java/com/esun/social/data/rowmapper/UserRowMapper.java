package com.esun.social.data.rowmapper;

import com.esun.social.business.model.User;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.RowMapper;

/** 將 {@code sp_user_*} 回傳的列轉為 {@link User}（不含密碼欄位）。 */
public class UserRowMapper implements RowMapper<User> {

    @Override
    public User mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new User(
                rs.getLong("user_id"),
                rs.getString("phone_number"),
                rs.getString("user_name"),
                rs.getString("email"),
                rs.getString("cover_image"),
                rs.getString("biography"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class),
                rs.getObject("deleted_at", LocalDateTime.class));
    }
}
