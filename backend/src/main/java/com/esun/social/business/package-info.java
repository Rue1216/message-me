/**
 * 業務層 —— 業務規則與交易邊界。
 *
 * <p>職責：權限判斷（只能編輯自己的發文）、輸入清洗（XSS）、密碼雜湊。
 * 交易邊界刻意不在此層：每個跨表動作都收斂為單一支內含 Transaction 的
 * Stored Procedure，因此不使用 {@code @Transactional}——理由詳見
 * {@link com.esun.social.business.service.PostService} 的類別註解。
 *
 * <p>{@code model} 之下是領域模型，只描述業務概念，不帶任何 HTTP 或 JDBC 的痕跡；
 * 這層因此能以純 Mockito 單元測試覆蓋，不需要啟動容器或資料庫。
 */
package com.esun.social.business;
