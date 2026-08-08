package com.esun.social.data.rowmapper;

import com.esun.social.business.model.Tag;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/** 將 {@code sp_tag_list_popular} 回傳的列轉為 {@link Tag}。 */
public class TagRowMapper implements RowMapper<Tag> {

    @Override
    public Tag mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Tag(rs.getLong("tag_id"), rs.getString("name"), rs.getInt("post_count"));
    }
}
