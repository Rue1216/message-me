/**
 * 展示層 —— HTTP 的入口與出口。
 *
 * <p>職責：路由對應、請求 DTO 的 Bean Validation、把業務層的領域模型轉為回應 DTO、
 * 決定 HTTP 狀態碼。<strong>不含業務規則，也不直接接觸資料庫。</strong>
 *
 * <p>依賴方向：{@code presentation → business → data}，反向依賴一律禁止。
 * 領域模型不會直接序列化給前端，一律經由 {@code dto.response} 轉換，
 * 避免 {@code password_hash} 這類欄位隨模型演進意外外洩。
 */
package com.esun.social.presentation;
