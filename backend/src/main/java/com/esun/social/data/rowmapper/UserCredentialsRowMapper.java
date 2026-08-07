package com.esun.social.data.rowmapper;

import com.esun.social.business.model.UserCredentials;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/** 將 {@code sp_user_find_by_phone} 回傳的列轉為 {@link UserCredentials}。 */
public class UserCredentialsRowMapper implements RowMapper<UserCredentials> {

    private final UserRowMapper userRowMapper = new UserRowMapper();

    @Override
    public UserCredentials mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new UserCredentials(
                userRowMapper.mapRow(rs, rowNum), rs.getString("password_hash"), rs.getString("password_salt"));
    }
}
