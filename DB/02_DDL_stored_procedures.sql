-- =============================================================================
-- 02_DDL_stored_procedures.sql — 全部 Stored Procedure
-- -----------------------------------------------------------------------------
-- 規格要求：所有資料庫存取一律透過 Stored Procedure；跨資料表的異動需以
--           Transaction 保證原子性。應用程式不組裝任何 SQL 字串。
--
-- 全域約定
--   1. 命名：sp_<資源>_<動作>。
--   2. 參數前綴 p_，區域變數前綴 v_，杜絕與資料表欄位同名造成的隱含遮蔽。
--   3. 新建 ID 與影響筆數一律以 OUT 參數回傳，呼叫端不需解析結果集。
--   4. 權限：涉及編輯 / 刪除者，SP 內以 AND user_id = p_user_id 再把關一次。
--      即使業務層的擁有者檢查被繞過，資料庫仍拒絕越權異動（雙重防護）。
--   5. **一律不使用動態 SQL**（全檔不出現 PREPARE / EXECUTE），
--      從根本消除 SQL 於資料庫端被重新組裝的注入路徑。
--   6. SQL SECURITY DEFINER：程序以建立者（root）權限執行，因此 app_user
--      僅需 EXECUTE 權限即可運作，無須任何資料表權限（見 01_DDL_schema.sql）。
--   7. 分頁參數在 SP 內夾限（1..100），避免呼叫端誤傳造成全表掃描。
--   8. 所有字串參數明確標註 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci。
--      未標註時，參數的定序取自「建立程序當下的連線定序」（MySQL 8 預設為
--      utf8mb4_0900_ai_ci），與資料表欄位的 utf8mb4_unicode_ci 比對會直接拋出
--      ERROR 1267 Illegal mix of collations。明確標註後，SP 的行為不再取決於
--      客戶端或 JDBC 驅動協商出的連線定序。
--
-- 錯誤契約（應用層據此對應 HTTP 狀態碼）
--   SQLSTATE 45000 / MYSQL_ERRNO 1644 + MESSAGE_TEXT 'PHONE_ALREADY_REGISTERED'
--       → 手機號碼已被註冊（409 Conflict）
--   ERROR 1062（duplicate key on uk_users_phone_number）
--       → 同上，併發註冊時由唯一鍵攔下
--   ERROR 1452（foreign key constraint fails）
--       → 對不存在的發文留言（404 Not Found）
--   OUT p_affected_rows = 0
--       → 目標不存在或不屬於此使用者（404 / 403，由業務層區分）
-- =============================================================================

USE `message_me`;

DELIMITER $$


-- =============================================================================
-- 使用者
-- =============================================================================

-- -----------------------------------------------------------------------------
-- sp_user_register — 註冊
-- 手機號碼唯一性以兩層把關：SP 內先行檢查（回傳明確的業務錯誤），
-- uk_users_phone_number 唯一鍵則攔下併發註冊造成的競態。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_user_register`$$
CREATE PROCEDURE `sp_user_register`(
    IN  p_phone_number  VARCHAR(20)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_user_name     VARCHAR(50)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_email         VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_password_hash VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_password_salt VARCHAR(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    OUT p_user_id       BIGINT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '註冊使用者，回傳新使用者 ID'
BEGIN
    DECLARE v_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO v_exists
      FROM `users`
     WHERE `phone_number` = p_phone_number;

    IF v_exists > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'PHONE_ALREADY_REGISTERED', MYSQL_ERRNO = 1644;
    END IF;

    INSERT INTO `users` (`phone_number`, `user_name`, `email`, `password_hash`, `password_salt`)
    VALUES (p_phone_number, p_user_name, p_email, p_password_hash, p_password_salt);

    SET p_user_id = LAST_INSERT_ID();
END$$


-- -----------------------------------------------------------------------------
-- sp_user_find_by_phone — 依手機號碼查詢（登入用）
-- 這是唯一會回傳 password_hash / password_salt 的程序，供密碼驗證使用。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_user_find_by_phone`$$
CREATE PROCEDURE `sp_user_find_by_phone`(
    IN p_phone_number VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '依手機號碼查詢使用者，含密碼雜湊與鹽，供登入驗證'
BEGIN
    SELECT `user_id`,
           `phone_number`,
           `user_name`,
           `email`,
           `password_hash`,
           `password_salt`,
           `cover_image`,
           `biography`,
           `created_at`,
           `updated_at`
      FROM `users`
     WHERE `phone_number` = p_phone_number;
END$$


-- -----------------------------------------------------------------------------
-- sp_user_find_by_id — 依 ID 查詢
-- 刻意不回傳 password_hash / password_salt：個人檔案的讀取路徑無需接觸憑證資料，
-- 減少敏感欄位在應用層流通的機會。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_user_find_by_id`$$
CREATE PROCEDURE `sp_user_find_by_id`(
    IN p_user_id BIGINT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '依 ID 查詢使用者，不含密碼雜湊與鹽'
BEGIN
    SELECT `user_id`,
           `phone_number`,
           `user_name`,
           `email`,
           `cover_image`,
           `biography`,
           `created_at`,
           `updated_at`
      FROM `users`
     WHERE `user_id` = p_user_id;
END$$


-- -----------------------------------------------------------------------------
-- sp_user_update_profile — 更新個人檔案
-- 採全欄位取代語意（對應 HTTP PUT）：傳入 NULL 即為清空該欄位，
-- 呼叫端需送出完整的檔案狀態。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_user_update_profile`$$
CREATE PROCEDURE `sp_user_update_profile`(
    IN  p_user_id        BIGINT,
    IN  p_user_name      VARCHAR(50)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_email          VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_biography      VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_cover_image    VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    OUT p_affected_rows  INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '更新個人檔案（全欄位取代），回傳影響筆數'
BEGIN
    UPDATE `users`
       SET `user_name`   = p_user_name,
           `email`       = p_email,
           `biography`   = p_biography,
           `cover_image` = p_cover_image
     WHERE `user_id` = p_user_id;

    SET p_affected_rows = ROW_COUNT();
END$$


-- =============================================================================
-- 發文
-- =============================================================================

-- -----------------------------------------------------------------------------
-- sp_post_create — 新增發文
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_create`$$
CREATE PROCEDURE `sp_post_create`(
    IN  p_user_id BIGINT,
    IN  p_content TEXT         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_image   VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    OUT p_post_id BIGINT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '新增發文，回傳新發文 ID'
BEGIN
    INSERT INTO `posts` (`user_id`, `content`, `image`)
    VALUES (p_user_id, p_content, p_image);

    SET p_post_id = LAST_INSERT_ID();
END$$


-- -----------------------------------------------------------------------------
-- sp_post_list — 分頁列出全部發文（時間軸，新到舊）
-- JOIN users 一次帶出作者資訊，避免列表頁的 N+1 查詢。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_list`$$
CREATE PROCEDURE `sp_post_list`(
    IN p_limit  INT,
    IN p_offset INT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '分頁列出全部發文，含作者資訊'
BEGIN
    -- 夾限分頁參數：單頁上限 100 筆，offset 不得為負
    SET p_limit  = LEAST(GREATEST(IFNULL(p_limit, 10), 1), 100);
    SET p_offset = GREATEST(IFNULL(p_offset, 0), 0);

    SELECT p.`post_id`,
           p.`user_id`,
           p.`content`,
           p.`image`,
           p.`comment_count`,
           p.`created_at`,
           p.`updated_at`,
           u.`user_name`   AS `author_name`,
           u.`cover_image` AS `author_cover_image`
      FROM `posts` p
      INNER JOIN `users` u ON u.`user_id` = p.`user_id`
     ORDER BY p.`created_at` DESC, p.`post_id` DESC
     LIMIT p_limit OFFSET p_offset;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_count — 發文總數（供分頁計算總頁數）
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_count`$$
CREATE PROCEDURE `sp_post_count`(
    OUT p_total BIGINT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '回傳發文總數'
BEGIN
    SELECT COUNT(*) INTO p_total FROM `posts`;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_find_by_id — 查詢單篇發文
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_find_by_id`$$
CREATE PROCEDURE `sp_post_find_by_id`(
    IN p_post_id BIGINT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '查詢單篇發文，含作者資訊'
BEGIN
    SELECT p.`post_id`,
           p.`user_id`,
           p.`content`,
           p.`image`,
           p.`comment_count`,
           p.`created_at`,
           p.`updated_at`,
           u.`user_name`   AS `author_name`,
           u.`cover_image` AS `author_cover_image`
      FROM `posts` p
      INNER JOIN `users` u ON u.`user_id` = p.`user_id`
     WHERE p.`post_id` = p_post_id;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_update — 編輯發文
-- WHERE 條件同時比對 post_id 與 user_id：非本人編輯時影響筆數為 0，資料不受異動。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_update`$$
CREATE PROCEDURE `sp_post_update`(
    IN  p_post_id       BIGINT,
    IN  p_user_id       BIGINT,
    IN  p_content       TEXT         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_image         VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    OUT p_affected_rows INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '編輯發文（僅限本人），回傳影響筆數'
BEGIN
    UPDATE `posts`
       SET `content` = p_content,
           `image`   = p_image
     WHERE `post_id` = p_post_id
       AND `user_id` = p_user_id;

    SET p_affected_rows = ROW_COUNT();
END$$


-- -----------------------------------------------------------------------------
-- sp_post_delete — 刪除發文與其全部留言【Transaction】
--
-- 跨兩張資料表：先刪 comments 再刪 posts（外鍵方向要求此順序）。
-- 若中途失敗，EXIT HANDLER 回滾並以 RESIGNAL 將原始錯誤上拋，
-- 讓應用層取得真實原因而非靜默失敗。
--
-- 越權情境同樣走回滾：非本人刪除時 posts 的影響筆數為 0，此時整筆交易回滾，
-- 連同已刪除的留言一併還原——因此「刪別人的發文」不會留下任何副作用。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_delete`$$
CREATE PROCEDURE `sp_post_delete`(
    IN  p_post_id       BIGINT,
    IN  p_user_id       BIGINT,
    OUT p_affected_rows INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '刪除發文與其全部留言（含 Transaction），回傳影響筆數'
BEGIN
    DECLARE v_deleted INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    DELETE FROM `comments`
     WHERE `post_id` = p_post_id;

    DELETE FROM `posts`
     WHERE `post_id` = p_post_id
       AND `user_id` = p_user_id;

    SET v_deleted = ROW_COUNT();

    IF v_deleted = 0 THEN
        -- 發文不存在或不屬於此使用者：還原上方已刪除的留言
        ROLLBACK;
    ELSE
        COMMIT;
    END IF;

    SET p_affected_rows = v_deleted;
END$$


-- =============================================================================
-- 留言
-- =============================================================================

-- -----------------------------------------------------------------------------
-- sp_comment_create — 新增留言並遞增留言數【Transaction】
--
-- 跨兩張資料表：comments 寫入 + posts.comment_count 遞增，兩者必須同進退，
-- 否則畫面顯示的留言數會與實際留言不符。
-- 對不存在的發文留言時，外鍵約束（ERROR 1452）觸發 EXIT HANDLER 回滾並上拋。
--
-- updated_at = updated_at 為刻意保留：該欄位設有 ON UPDATE CURRENT_TIMESTAMP，
-- 若不明確賦值，留言會意外更動發文的「編輯時間」。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_comment_create`$$
CREATE PROCEDURE `sp_comment_create`(
    IN  p_post_id    BIGINT,
    IN  p_user_id    BIGINT,
    IN  p_content    TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    OUT p_comment_id BIGINT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '新增留言並遞增發文留言數（含 Transaction），回傳新留言 ID'
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    INSERT INTO `comments` (`post_id`, `user_id`, `content`)
    VALUES (p_post_id, p_user_id, p_content);

    SET p_comment_id = LAST_INSERT_ID();

    UPDATE `posts`
       SET `comment_count` = `comment_count` + 1,
           `updated_at`    = `updated_at`
     WHERE `post_id` = p_post_id;

    COMMIT;
END$$


-- -----------------------------------------------------------------------------
-- sp_comment_list_by_post — 分頁列出單篇發文的留言（舊到新）
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_comment_list_by_post`$$
CREATE PROCEDURE `sp_comment_list_by_post`(
    IN p_post_id BIGINT,
    IN p_limit   INT,
    IN p_offset  INT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '分頁列出單篇發文的留言，含留言者資訊'
BEGIN
    SET p_limit  = LEAST(GREATEST(IFNULL(p_limit, 10), 1), 100);
    SET p_offset = GREATEST(IFNULL(p_offset, 0), 0);

    SELECT c.`comment_id`,
           c.`post_id`,
           c.`user_id`,
           c.`content`,
           c.`created_at`,
           u.`user_name`   AS `author_name`,
           u.`cover_image` AS `author_cover_image`
      FROM `comments` c
      INNER JOIN `users` u ON u.`user_id` = c.`user_id`
     WHERE c.`post_id` = p_post_id
     ORDER BY c.`created_at` ASC, c.`comment_id` ASC
     LIMIT p_limit OFFSET p_offset;
END$$


-- -----------------------------------------------------------------------------
-- sp_comment_count_by_post — 單篇發文的留言總數
-- 直接數 comments，不讀 posts.comment_count：分頁計算需要權威值，
-- 同時可作為反正規化欄位是否失準的對照。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_comment_count_by_post`$$
CREATE PROCEDURE `sp_comment_count_by_post`(
    IN  p_post_id BIGINT,
    OUT p_total   BIGINT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '回傳單篇發文的留言總數'
BEGIN
    SELECT COUNT(*) INTO p_total
      FROM `comments`
     WHERE `post_id` = p_post_id;
END$$


-- -----------------------------------------------------------------------------
-- sp_comment_delete — 刪除留言並遞減留言數【Transaction】
--
-- 以聚合函式取得所屬發文 ID：無論是否有資料都必定回傳一列（查無資料時為 NULL），
-- 因此不會觸發 SELECT ... INTO 在查無資料時的 NOT FOUND 警告。
-- 僅在確實刪除了留言時才遞減計數，避免重複刪除造成計數虛減；
-- GREATEST(..., 0) 則保證計數不會因任何意外情形變成負數。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_comment_delete`$$
CREATE PROCEDURE `sp_comment_delete`(
    IN  p_comment_id    BIGINT,
    IN  p_user_id       BIGINT,
    OUT p_affected_rows INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '刪除留言並遞減發文留言數（含 Transaction），回傳影響筆數'
BEGIN
    DECLARE v_post_id BIGINT DEFAULT NULL;
    DECLARE v_deleted INT    DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT MAX(`post_id`) INTO v_post_id
      FROM `comments`
     WHERE `comment_id` = p_comment_id
       AND `user_id`    = p_user_id;

    DELETE FROM `comments`
     WHERE `comment_id` = p_comment_id
       AND `user_id`    = p_user_id;

    SET v_deleted = ROW_COUNT();

    IF v_deleted > 0 THEN
        UPDATE `posts`
           SET `comment_count` = GREATEST(`comment_count` - 1, 0),
               `updated_at`    = `updated_at`
         WHERE `post_id` = v_post_id;
    END IF;

    COMMIT;

    SET p_affected_rows = v_deleted;
END$$


DELIMITER ;
