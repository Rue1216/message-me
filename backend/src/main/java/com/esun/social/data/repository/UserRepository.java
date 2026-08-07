package com.esun.social.data.repository;

import com.esun.social.business.model.Activity;
import com.esun.social.business.model.User;
import com.esun.social.business.model.UserCredentials;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.data.rowmapper.ActivityRowMapper;
import com.esun.social.data.rowmapper.UserCredentialsRowMapper;
import com.esun.social.data.rowmapper.UserRowMapper;
import com.esun.social.data.support.StoredProcedureCallFactory;
import com.esun.social.data.support.StoredProcedureErrors;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

/**
 * 使用者資料存取，全部經由 {@code sp_user_*} Stored Procedure。
 *
 * <p>四個呼叫物件在建構時就建立完成並重複使用：{@code SimpleJdbcCall} 編譯後即為執行緒安全，
 * 每次請求重建只是重複付出建立成本。
 */
@Repository
public class UserRepository {

    private static final String RESULT_SET_KEY = "users";
    private static final String ACTIVITY_RESULT_SET_KEY = "activities";

    private final SimpleJdbcCall registerCall;
    private final SimpleJdbcCall findByPhoneCall;
    private final SimpleJdbcCall findByIdCall;
    private final SimpleJdbcCall findCredentialsByIdCall;
    private final SimpleJdbcCall updateProfileCall;
    private final SimpleJdbcCall changePasswordCall;
    private final SimpleJdbcCall softDeleteCall;
    private final SimpleJdbcCall activityListCall;
    private final SimpleJdbcCall activityCountCall;

    public UserRepository(StoredProcedureCallFactory callFactory) {
        this.findCredentialsByIdCall = callFactory
                .forProcedure("sp_user_find_credentials_by_id")
                .declareParameters(new SqlParameter("p_user_id", Types.BIGINT))
                .returningResultSet(RESULT_SET_KEY, new UserCredentialsRowMapper());

        this.changePasswordCall = callFactory
                .forProcedure("sp_user_change_password")
                .declareParameters(
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlParameter("p_password_hash", Types.VARCHAR),
                        new SqlParameter("p_password_salt", Types.VARCHAR),
                        new SqlOutParameter("p_affected_rows", Types.INTEGER));

        this.softDeleteCall = callFactory
                .forProcedure("sp_user_soft_delete")
                .declareParameters(
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlParameter("p_anonymized_name", Types.VARCHAR),
                        new SqlOutParameter("p_affected_rows", Types.INTEGER));

        this.activityListCall = callFactory
                .forProcedure("sp_user_activity_list")
                .declareParameters(
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlParameter("p_limit", Types.INTEGER),
                        new SqlParameter("p_offset", Types.INTEGER))
                .returningResultSet(ACTIVITY_RESULT_SET_KEY, new ActivityRowMapper());

        this.activityCountCall = callFactory
                .forProcedure("sp_user_activity_count")
                .declareParameters(
                        new SqlParameter("p_user_id", Types.BIGINT), new SqlOutParameter("p_total", Types.BIGINT));

        this.registerCall = callFactory
                .forProcedure("sp_user_register")
                .declareParameters(
                        new SqlParameter("p_phone_number", Types.VARCHAR),
                        new SqlParameter("p_user_name", Types.VARCHAR),
                        new SqlParameter("p_email", Types.VARCHAR),
                        new SqlParameter("p_password_hash", Types.VARCHAR),
                        new SqlParameter("p_password_salt", Types.VARCHAR),
                        new SqlOutParameter("p_user_id", Types.BIGINT));

        this.findByPhoneCall = callFactory
                .forProcedure("sp_user_find_by_phone")
                .declareParameters(new SqlParameter("p_phone_number", Types.VARCHAR))
                .returningResultSet(RESULT_SET_KEY, new UserCredentialsRowMapper());

        this.findByIdCall = callFactory
                .forProcedure("sp_user_find_by_id")
                .declareParameters(new SqlParameter("p_user_id", Types.BIGINT))
                .returningResultSet(RESULT_SET_KEY, new UserRowMapper());

        this.updateProfileCall = callFactory
                .forProcedure("sp_user_update_profile")
                .declareParameters(
                        new SqlParameter("p_user_id", Types.BIGINT),
                        new SqlParameter("p_user_name", Types.VARCHAR),
                        new SqlParameter("p_email", Types.VARCHAR),
                        new SqlParameter("p_biography", Types.VARCHAR),
                        new SqlParameter("p_cover_image", Types.VARCHAR),
                        new SqlOutParameter("p_affected_rows", Types.INTEGER));
    }

    /**
     * 註冊新使用者。
     *
     * <p>手機重複有兩條路徑：SP 內的前置檢查以 SIGNAL 拋出，併發時則由唯一鍵擋下（1062）。
     * 兩者在此收斂成同一個業務例外——對呼叫端而言就只是「這個號碼已經有人用了」。
     *
     * @return 新使用者的 ID
     * @throws BusinessException 手機號碼已被註冊
     */
    public long register(String phoneNumber, String userName, String email, String passwordHash, String passwordSalt) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_phone_number", phoneNumber)
                .addValue("p_user_name", userName)
                .addValue("p_email", email)
                .addValue("p_password_hash", passwordHash)
                .addValue("p_password_salt", passwordSalt);
        try {
            Map<String, Object> result = registerCall.execute(parameters);
            return ((Number) result.get("p_user_id")).longValue();
        } catch (DataAccessException ex) {
            if (StoredProcedureErrors.isSignalled(ex, ErrorCode.PHONE_ALREADY_REGISTERED)
                    || StoredProcedureErrors.isMySqlError(ex, StoredProcedureErrors.DUPLICATE_ENTRY)) {
                throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED, null, ex);
            }
            throw ex;
        }
    }

    /** 依手機號碼查詢，含密碼雜湊與鹽。僅供登入驗證使用。 */
    public Optional<UserCredentials> findByPhoneNumber(String phoneNumber) {
        Map<String, Object> result =
                findByPhoneCall.execute(new MapSqlParameterSource("p_phone_number", phoneNumber));
        return firstOf(result);
    }

    /** 依 ID 查詢，不含密碼欄位。 */
    public Optional<User> findById(long userId) {
        Map<String, Object> result = findByIdCall.execute(new MapSqlParameterSource("p_user_id", userId));
        return firstOf(result);
    }

    /**
     * 更新個人檔案（全欄位取代語意，對應 HTTP PUT）。
     *
     * @return 是否確實更新了資料；{@code false} 代表該使用者不存在
     */
    public boolean updateProfile(long userId, String userName, String email, String biography, String coverImage) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_user_id", userId)
                .addValue("p_user_name", userName)
                .addValue("p_email", email)
                .addValue("p_biography", biography)
                .addValue("p_cover_image", coverImage);
        Map<String, Object> result = updateProfileCall.execute(parameters);
        return ((Number) result.get("p_affected_rows")).intValue() > 0;
    }

    /**
     * 依 ID 查詢，含密碼雜湊與鹽。僅供「修改密碼時驗證舊密碼」使用。
     *
     * <p>與 {@link #findByPhoneNumber} 並列為僅有的兩條會取得憑證的路徑，兩者都由專屬的 SP 支援，
     * 使敏感欄位的流通範圍在資料層即可一眼盤點。
     */
    public Optional<UserCredentials> findCredentialsById(long userId) {
        Map<String, Object> result = findCredentialsByIdCall.execute(new MapSqlParameterSource("p_user_id", userId));
        return firstOf(result);
    }

    /**
     * 更新密碼雜湊與鹽。鹽一併更換，理由見 {@code sp_user_change_password} 的註解。
     *
     * @return 是否確實更新；使用者不存在或已刪除時為 {@code false}
     */
    public boolean changePassword(long userId, String passwordHash, String passwordSalt) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_user_id", userId)
                .addValue("p_password_hash", passwordHash)
                .addValue("p_password_salt", passwordSalt);
        Map<String, Object> result = changePasswordCall.execute(parameters);
        return ((Number) result.get("p_affected_rows")).intValue() > 0;
    }

    /**
     * 軟刪除帳號並匿名化身分欄位。發文與留言原樣保留。
     *
     * @param anonymizedName 取代原使用者名稱的顯示字串。文案屬於應用層的決定，不寫死在 SQL 中
     * @return 是否確實刪除；使用者不存在或已刪除時為 {@code false}（重複刪除為 no-op）
     */
    public boolean softDelete(long userId, String anonymizedName) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_user_id", userId)
                .addValue("p_anonymized_name", anonymizedName);
        Map<String, Object> result = softDeleteCall.execute(parameters);
        return ((Number) result.get("p_affected_rows")).intValue() > 0;
    }

    /** 某使用者的發文與留言合併時間軸，新到舊。 */
    @SuppressWarnings("unchecked")
    public List<Activity> findActivityPage(long userId, int limit, int offset) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("p_user_id", userId)
                .addValue("p_limit", limit)
                .addValue("p_offset", offset);
        List<Activity> activities =
                (List<Activity>) activityListCall.execute(parameters).get(ACTIVITY_RESULT_SET_KEY);
        return activities == null ? List.of() : activities;
    }

    public long countActivities(long userId) {
        Map<String, Object> result = activityCountCall.execute(new MapSqlParameterSource("p_user_id", userId));
        return ((Number) result.get("p_total")).longValue();
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> firstOf(Map<String, Object> result) {
        List<T> rows = (List<T>) result.get(RESULT_SET_KEY);
        return rows == null || rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
