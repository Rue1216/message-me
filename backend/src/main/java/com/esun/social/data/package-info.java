/**
 * 資料層 —— 唯一與資料庫對話的地方。
 *
 * <p>規格要求所有資料庫存取都經過 Stored Procedure，因此本層<strong>只以
 * {@code SimpleJdbcCall} 呼叫 {@code DB/02_DDL_stored_procedures.sql} 中的程序</strong>，
 * 不出現任何手寫 SQL 字串，從源頭消除 SQL Injection 的拼接路徑。
 *
 * <p>參數一律透過 {@code CallableStatement} 綁定；程序名稱與參數宣告寫死在程式碼中，
 * 不接受外部輸入。呼叫物件的建立集中在
 * {@link com.esun.social.data.support.StoredProcedureCallFactory}。
 */
package com.esun.social.data;
