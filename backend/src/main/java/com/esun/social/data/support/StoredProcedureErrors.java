package com.esun.social.data.support;

import com.esun.social.common.exception.ErrorCode;
import java.sql.SQLException;
import org.springframework.dao.DataAccessException;

/**
 * 判讀 Stored Procedure 拋出的錯誤。
 *
 * <p>{@code DB/02_DDL_stored_procedures.sql} 開頭定義了錯誤契約：業務錯誤以
 * {@code SIGNAL SQLSTATE '45000'} 搭配 {@code MESSAGE_TEXT} 傳遞，而 {@code MESSAGE_TEXT}
 * 刻意寫成與 {@link ErrorCode} 同名的字串，因此兩邊不需要維護對照表。
 *
 * <p>Spring 的例外轉譯器不認得 SQLSTATE 45000（那是使用者自訂區段），會包成
 * {@code UncategorizedSQLException}，所以得自己往下挖到 {@link SQLException} 判讀。
 */
public final class StoredProcedureErrors {

    /** 唯一鍵衝突。併發註冊時 SP 的前置檢查會被穿過，由唯一鍵在此攔下。 */
    public static final int DUPLICATE_ENTRY = 1062;

    /** 外鍵約束失敗。例如對不存在的發文留言。 */
    public static final int FOREIGN_KEY_VIOLATION = 1452;

    private StoredProcedureErrors() {}

    /** 這個例外是否來自 SP 以 SIGNAL 拋出的指定業務錯誤。 */
    public static boolean isSignalled(DataAccessException ex, ErrorCode errorCode) {
        SQLException sqlException = findSqlException(ex);
        return sqlException != null
                && "45000".equals(sqlException.getSQLState())
                && sqlException.getMessage() != null
                && sqlException.getMessage().contains(errorCode.name());
    }

    /** 這個例外是否為指定的 MySQL 錯誤碼。 */
    public static boolean isMySqlError(DataAccessException ex, int errorNumber) {
        SQLException sqlException = findSqlException(ex);
        return sqlException != null && sqlException.getErrorCode() == errorNumber;
    }

    private static SQLException findSqlException(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }
}
