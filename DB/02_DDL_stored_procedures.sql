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

-- 連線字元集，理由見 01_DDL_schema.sql 的同名段落；不宣告會使 COMMENT 中的中文雙重編碼。
SET NAMES utf8mb4;

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
--
-- deleted_at IS NULL 是軟刪除帳號無法再登入的唯一把關點：查無資料時，業務層走的是
-- 與「手機號碼不存在」完全相同的路徑，回應時間與訊息皆一致，
-- 不會讓人從登入行為推測出某個號碼曾經註冊過。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_user_find_by_phone`$$
CREATE PROCEDURE `sp_user_find_by_phone`(
    IN p_phone_number VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '依手機號碼查詢有效使用者，含密碼雜湊與鹽，供登入驗證'
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
           `updated_at`,
           -- 依 WHERE 條件此欄必定為 NULL，回傳它純粹是為了讓三支 user 查詢的投影一致，
           -- UserRowMapper 才能只有一份。
           `deleted_at`
      FROM `users`
     WHERE `phone_number` = p_phone_number
       AND `deleted_at` IS NULL;
END$$


-- -----------------------------------------------------------------------------
-- sp_user_find_credentials_by_id — 依 ID 取出憑證（修改密碼用）
--
-- 修改密碼必須先驗證舊密碼，而 PBKDF2 的比對只能在應用層做（資料庫不持有雜湊參數）。
-- sp_user_find_by_id 刻意不回傳憑證欄位，因此另開這一支專用程序：
-- 讓「會讀到密碼雜湊」的路徑保持稀少且一眼可數，而不是放寬既有查詢的投影。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_user_find_credentials_by_id`$$
CREATE PROCEDURE `sp_user_find_credentials_by_id`(
    IN p_user_id BIGINT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '依 ID 取出密碼雜湊與鹽，供修改密碼時驗證舊密碼'
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
           `updated_at`,
           `deleted_at`
      FROM `users`
     WHERE `user_id` = p_user_id
       AND `deleted_at` IS NULL;
END$$


-- -----------------------------------------------------------------------------
-- sp_user_find_by_id — 依 ID 查詢
-- 刻意不回傳 password_hash / password_salt：個人檔案的讀取路徑無需接觸憑證資料，
-- 減少敏感欄位在應用層流通的機會。
--
-- 這裡**不**過濾 deleted_at：已刪除帳號的發文與留言仍留在別人的討論串中，
-- 點進作者頁時應該看得到一個「已刪除的使用者」而不是 404。
-- 身分欄位已於 sp_user_soft_delete 中就地抹除，回傳它們不會洩漏任何個資；
-- deleted_at 一併回傳，讓展示層知道要標示為已刪除。
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
           `updated_at`,
           `deleted_at`
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
     WHERE `user_id` = p_user_id
       AND `deleted_at` IS NULL;

    SET p_affected_rows = ROW_COUNT();
END$$


-- -----------------------------------------------------------------------------
-- sp_user_change_password — 修改密碼
--
-- 舊密碼的驗證在應用層完成（PBKDF2 的迭代次數與比對方式由 PasswordHasher 持有，
-- 資料庫不重複實作一份密碼學邏輯）。本程序只負責寫入新的雜湊與鹽。
--
-- 鹽一併更換而非沿用舊鹽：換密碼時重新產生鹽，可確保「同一個人先後用過的密碼」
-- 在資料庫中不會呈現任何可比對的關聯。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_user_change_password`$$
CREATE PROCEDURE `sp_user_change_password`(
    IN  p_user_id       BIGINT,
    IN  p_password_hash VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_password_salt VARCHAR(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    OUT p_affected_rows INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '更新密碼雜湊與鹽，回傳影響筆數'
BEGIN
    UPDATE `users`
       SET `password_hash` = p_password_hash,
           `password_salt` = p_password_salt
     WHERE `user_id` = p_user_id
       AND `deleted_at` IS NULL;

    SET p_affected_rows = ROW_COUNT();
END$$


-- -----------------------------------------------------------------------------
-- sp_user_soft_delete — 刪除帳號（軟刪除 + 匿名化）
--
-- 語意：帳號消失，內容留下。發文與留言原樣保留在別人的討論串中，作者顯示為
-- 「已刪除的使用者」——硬刪除會在他人的對話裡挖出一個個缺口，且無法復原。
-- 稽核軌跡（誰在什麼時候說了什麼）因此完整保留。
--
-- 匿名化的範圍即為「所有能識別出這個人的欄位」：
--   user_name    → 呼叫端傳入的佔位字串（文案屬於展示層的決定，不寫死在 SQL 中）
--   phone_number → deleted_<user_id>，長度必定在 VARCHAR(20) 內。
--                  這一步同時釋放 uk_users_phone_number，原手機號碼可重新註冊。
--   email / biography / cover_image → 清空
--   password_hash / password_salt   → 覆寫為隨機值，使原雜湊不再存在於資料庫中
--
-- 本程序只異動 users 一張表，單一 UPDATE 敘述本身即具原子性，
-- 因此**不需要**顯式 Transaction——加上去只會是沒有作用的裝飾。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_user_soft_delete`$$
CREATE PROCEDURE `sp_user_soft_delete`(
    IN  p_user_id           BIGINT,
    IN  p_anonymized_name   VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    OUT p_affected_rows     INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '軟刪除帳號並匿名化身分欄位，保留發文與留言'
BEGIN
    UPDATE `users`
       SET `deleted_at`    = CURRENT_TIMESTAMP,
           `user_name`     = p_anonymized_name,
           `phone_number`  = CONCAT('deleted_', `user_id`),
           `email`         = NULL,
           `biography`     = NULL,
           `cover_image`   = NULL,
           `password_hash` = TO_BASE64(RANDOM_BYTES(32)),
           `password_salt` = TO_BASE64(RANDOM_BYTES(32))
     WHERE `user_id` = p_user_id
       AND `deleted_at` IS NULL;

    SET p_affected_rows = ROW_COUNT();
END$$


-- -----------------------------------------------------------------------------
-- sp_user_activity_list — 個人頁的合併動態（發文與留言交錯，新到舊）
--
-- 兩種活動來自不同資料表，以 UNION ALL 併成單一時間軸。留言那一支 JOIN 回 posts
-- 與 users，帶出原文摘要與原作者，讓「我在誰的哪篇發文下留了什麼」在一列裡說完，
-- 前端不需要為每則留言再打一次 API。
--
-- 這一支刻意採 offset 分頁，而非時間軸所用的 keyset：
-- 跨兩張資料表的複合游標需要同時編碼「時間 + 來源表 + 該表的主鍵」，脆弱且難以驗證；
-- 而個人頁的資料量受單一使用者的產出量所限，offset 的掃描成本完全在可接受範圍內。
-- 排序鍵補上 activity_type 與 activity_id，確保同一毫秒的發文與留言有穩定的先後。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_user_activity_list`$$
CREATE PROCEDURE `sp_user_activity_list`(
    IN p_user_id BIGINT,
    IN p_limit   INT,
    IN p_offset  INT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '列出某使用者的發文與留言合併時間軸，新到舊'
BEGIN
    SET p_limit  = LEAST(GREATEST(IFNULL(p_limit, 10), 1), 100);
    SET p_offset = GREATEST(IFNULL(p_offset, 0), 0);

    SELECT `activity_type`,
           `activity_id`,
           `post_id`,
           `content`,
           `image`,
           `comment_count`,
           `like_count`,
           `created_at`,
           `post_excerpt`,
           `post_author_name`
      FROM (
            -- UNION 的兩支必須逐欄具有相容的定序，否則 MySQL 直接拋出
            -- ERROR 1271 Illegal mix of collations for operation 'UNION'。
            -- 字串常值與 CAST(NULL AS CHAR) 都會取連線預設的 utf8mb4_0900_ai_ci，
            -- 與另一支來自資料表欄位的 utf8mb4_unicode_ci 相衝；因此凡是不直接
            -- 取自欄位的字串運算式，都在此明確標註定序。
            SELECT CAST('POST' AS CHAR(10) CHARACTER SET utf8mb4)
                       COLLATE utf8mb4_unicode_ci             AS `activity_type`,
                   p.`post_id`                                AS `activity_id`,
                   p.`post_id`                                AS `post_id`,
                   p.`content`                                AS `content`,
                   p.`image`                                  AS `image`,
                   p.`comment_count`                          AS `comment_count`,
                   p.`like_count`                             AS `like_count`,
                   p.`created_at`                             AS `created_at`,
                   CAST(NULL AS CHAR(200) CHARACTER SET utf8mb4)
                       COLLATE utf8mb4_unicode_ci             AS `post_excerpt`,
                   CAST(NULL AS CHAR(50) CHARACTER SET utf8mb4)
                       COLLATE utf8mb4_unicode_ci             AS `post_author_name`
              FROM `posts` p
             WHERE p.`user_id` = p_user_id

            UNION ALL

            SELECT CAST('COMMENT' AS CHAR(10) CHARACTER SET utf8mb4)
                       COLLATE utf8mb4_unicode_ci,
                   c.`comment_id`,
                   c.`post_id`,
                   c.`content`,
                   CAST(NULL AS CHAR(500) CHARACTER SET utf8mb4)
                       COLLATE utf8mb4_unicode_ci,
                   0,
                   0,
                   c.`created_at`,
                   LEFT(op.`content`, 200),
                   ou.`user_name`
              FROM `comments` c
              INNER JOIN `posts` op ON op.`post_id` = c.`post_id`
              INNER JOIN `users` ou ON ou.`user_id` = op.`user_id`
             WHERE c.`user_id` = p_user_id
           ) AS `activities`
     ORDER BY `created_at` DESC, `activity_type` ASC, `activity_id` DESC
     LIMIT p_limit OFFSET p_offset;
END$$


-- -----------------------------------------------------------------------------
-- sp_user_activity_count — 合併動態的總筆數（供 offset 分頁計算總頁數）
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_user_activity_count`$$
CREATE PROCEDURE `sp_user_activity_count`(
    IN  p_user_id BIGINT,
    OUT p_total   BIGINT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '回傳某使用者的發文與留言總筆數'
BEGIN
    SELECT (SELECT COUNT(*) FROM `posts`    WHERE `user_id` = p_user_id)
         + (SELECT COUNT(*) FROM `comments` WHERE `user_id` = p_user_id)
      INTO p_total;
END$$


-- =============================================================================
-- 發文
-- =============================================================================

-- -----------------------------------------------------------------------------
-- sp_post_create — 新增發文並掛上標籤【Transaction】
--
-- 跨四張資料表：posts 寫入 + tags upsert + post_tags 關聯 + tags.post_count 遞增。
-- 四者必須同進退，否則會出現「標籤存在但沒有任何發文用它」或「使用次數與實際不符」
-- 這類無法自我修復的髒資料。
--
-- 標籤名稱由發文者在標籤欄位指定，經業務層驗證並正規化（見 TagNormalizer）後以 JSON 陣列傳入。
-- 正規化放在業務層而非 SQL 中，是因為規格要求的是「透過 Stored Procedure 存取資料庫」，
-- 而不是「用 SQL 做字串處理」——在 SP 裡以 WHILE + SUBSTRING_INDEX 手工切字串，
-- 只會換來一段難讀且難測的迴圈。這裡改用 MySQL 8 的 JSON_TABLE 把陣列展開成資料列，
-- 後續就是三句一般的集合運算。
--
-- JSON_TABLE 的欄位必須明確標註 CHARACTER SET / COLLATE：不標註時其定序取自連線
-- （MySQL 8 預設 utf8mb4_0900_ai_ci），與 tags.name 的 utf8mb4_unicode_ci 比對會
-- 直接拋出 ERROR 1267。理由與檔頭第 8 點相同，只是這次的來源是 JSON 而非參數。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_create`$$
CREATE PROCEDURE `sp_post_create`(
    IN  p_user_id   BIGINT,
    IN  p_content   TEXT         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_image     VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_tags_json TEXT         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    OUT p_post_id   BIGINT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '新增發文並掛上標籤（含 Transaction），回傳新發文 ID'
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    INSERT INTO `posts` (`user_id`, `content`, `image`)
    VALUES (p_user_id, p_content, p_image);

    SET p_post_id = LAST_INSERT_ID();

    -- 尚未存在的標籤先建立；已存在者由 uk_tags_name 擋下，IGNORE 使其成為 no-op
    INSERT IGNORE INTO `tags` (`name`)
    SELECT jt.`name`
      FROM JSON_TABLE(
               CAST(IFNULL(p_tags_json, '[]') AS JSON),
               '$[*]' COLUMNS (
                   `name` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PATH '$'
               )
           ) AS jt;

    INSERT IGNORE INTO `post_tags` (`post_id`, `tag_id`)
    SELECT p_post_id, t.`tag_id`
      FROM JSON_TABLE(
               CAST(IFNULL(p_tags_json, '[]') AS JSON),
               '$[*]' COLUMNS (
                   `name` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PATH '$'
               )
           ) AS jt
      INNER JOIN `tags` t ON t.`name` = jt.`name`;

    -- 以實際落地的關聯為準遞增，而非以傳入的陣列為準：
    -- 重複的標籤名稱已被 post_tags 的主鍵收斂成一筆，計數才不會多算。
    UPDATE `tags` t
       SET t.`post_count` = t.`post_count` + 1
     WHERE t.`tag_id` IN (SELECT pt.`tag_id`
                            FROM `post_tags` pt
                           WHERE pt.`post_id` = p_post_id);

    COMMIT;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_list_cursor — 時間軸（新到舊），keyset 分頁
--
-- 取代原本的 offset 分頁。動態牆會持續有新發文插到最前面，offset 分頁在這種資料上
-- 必然出錯：使用者讀第 1 頁的同時有人發了文，第 2 頁的起點就整體右移一筆，
-- 於是同一則發文出現兩次。keyset 以「上一頁最後一筆的位置」為界，不受插入影響。
--
-- 游標是 (created_at, post_id) 這組複合鍵。單用 created_at 不夠——同一秒內的多筆
-- 發文會在邊界上互相遮蔽；補上 post_id 作為決勝欄位後，排序全序且游標唯一。
-- 比較條件寫成展開形式而非 (a,b) < (c,d) 的列比較，是為了讓最佳化器確實使用
-- idx_posts_created_at_post_id（MySQL 對列比較的索引下推支援並不一致）。
--
-- 回傳 p_limit + 1 筆：多出來的那一筆不給使用者看，只用來告訴呼叫端「後面還有」，
-- 省去一次額外的 COUNT 查詢。呼叫端負責裁掉它（見 PostRepository.findPageByCursor）。
--
-- p_viewer_id 為觀看者，未登入時傳 NULL：EXISTS 子查詢在 NULL 下恆為偽，
-- 訪客看到的 liked_by_me 一律是 false，不需要另外分支。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_list_cursor`$$
CREATE PROCEDURE `sp_post_list_cursor`(
    IN p_viewer_id         BIGINT,
    IN p_cursor_created_at DATETIME,
    IN p_cursor_post_id    BIGINT,
    IN p_limit             INT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '時間軸 keyset 分頁，多回傳一筆供呼叫端判斷是否還有下一頁'
BEGIN
    SET p_limit = LEAST(GREATEST(IFNULL(p_limit, 10), 1), 100);

    SELECT v.`post_id`,
           v.`user_id`,
           v.`content`,
           v.`image`,
           v.`comment_count`,
           v.`like_count`,
           v.`created_at`,
           v.`updated_at`,
           v.`author_name`,
           v.`author_cover_image`,
           v.`author_deleted_at`,
           v.`tag_names`,
           EXISTS (SELECT 1
                     FROM `post_likes` pl
                    WHERE pl.`post_id` = v.`post_id`
                      AND pl.`user_id` = p_viewer_id) AS `liked_by_me`
      FROM `v_post_detail` v
     WHERE p_cursor_created_at IS NULL
        OR v.`created_at` < p_cursor_created_at
        OR (v.`created_at` = p_cursor_created_at AND v.`post_id` < p_cursor_post_id)
     ORDER BY v.`created_at` DESC, v.`post_id` DESC
     LIMIT p_limit;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_search — 關鍵字搜尋，keyset 分頁
--
-- 以 FULLTEXT + ngram 解析器檢索。中文沒有以空白分隔的詞界，預設解析器會把整段
-- 內容視為單一詞元，導致任何查詢都搜不到；ngram 將內容切成固定長度的字元組，
-- 才使中文全文檢索成立（索引定義見 01_DDL_schema.sql 的 ft_posts_content）。
--
-- 採 NATURAL LANGUAGE MODE 而非 BOOLEAN MODE：後者會把 + - * " ( ) 等字元解讀為
-- 運算子，使用者輸入的 "C++" 之類的內容會被當成查詢語法而得到意料外的結果。
-- 這無關 SQL Injection（關鍵字始終是繫結參數），但是實實在在的正確性問題。
--
-- ngram_token_size 預設為 2，因此單一字元的關鍵字不會產生任何詞元、永遠無法命中。
-- 這種情形改走 LIKE：雖然吃不到索引，但單字搜尋在本專案的資料量下無足輕重，
-- 總比讓使用者搜「貓」卻得到空結果好。LIKE 的萬用字元（% _ \）先行跳脫，
-- 否則輸入一個 % 會 match 全部發文。
--
-- 比對條件寫成針對 posts 的子查詢，而不是直接對 v_post_detail 下 MATCH：
-- MySQL 的全文檢索只能作用在實體資料表上，透過檢視表使用會直接得到
-- ERROR 1214 The used table type doesn't support FULLTEXT indexes。
-- 因此這裡由基礎資料表篩出符合的 post_id，投影仍交給檢視表統一處理。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_search`$$
CREATE PROCEDURE `sp_post_search`(
    IN p_viewer_id         BIGINT,
    IN p_keyword           VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN p_cursor_created_at DATETIME,
    IN p_cursor_post_id    BIGINT,
    IN p_limit             INT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '依關鍵字搜尋發文，keyset 分頁，多回傳一筆供判斷是否還有下一頁'
BEGIN
    -- 區域變數同樣必須明確標註定序（檔頭第 8 點）：未標註時取連線預設的
    -- utf8mb4_0900_ai_ci，與 posts.content 的 utf8mb4_unicode_ci 相比會直接拋出
    -- ERROR 1270 Illegal mix of collations for operation 'like'。
    DECLARE v_like_pattern VARCHAR(310) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL;

    SET p_limit = LEAST(GREATEST(IFNULL(p_limit, 10), 1), 100);

    -- 跳脫順序不可調換：反斜線必須先跳脫，否則會把後續補上的跳脫字元再跳脫一次
    SET v_like_pattern = CONCAT('%',
                                REPLACE(REPLACE(REPLACE(IFNULL(p_keyword, ''),
                                        '\\', '\\\\'),
                                        '%',  '\\%'),
                                        '_',  '\\_'),
                                '%');

    SELECT v.`post_id`,
           v.`user_id`,
           v.`content`,
           v.`image`,
           v.`comment_count`,
           v.`like_count`,
           v.`created_at`,
           v.`updated_at`,
           v.`author_name`,
           v.`author_cover_image`,
           v.`author_deleted_at`,
           v.`tag_names`,
           EXISTS (SELECT 1
                     FROM `post_likes` pl
                    WHERE pl.`post_id` = v.`post_id`
                      AND pl.`user_id` = p_viewer_id) AS `liked_by_me`
      FROM `v_post_detail` v
     WHERE v.`post_id` IN (
               SELECT p.`post_id`
                 FROM `posts` p
                WHERE (CHAR_LENGTH(p_keyword) >= 2
                       AND MATCH (p.`content`) AGAINST (p_keyword IN NATURAL LANGUAGE MODE))
                   OR (CHAR_LENGTH(p_keyword) < 2
                       AND p.`content` LIKE v_like_pattern ESCAPE '\\')
           )
       AND (p_cursor_created_at IS NULL
            OR v.`created_at` < p_cursor_created_at
            OR (v.`created_at` = p_cursor_created_at AND v.`post_id` < p_cursor_post_id))
     ORDER BY v.`created_at` DESC, v.`post_id` DESC
     LIMIT p_limit;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_list_by_tag — 某標籤底下的發文，keyset 分頁
-- 標籤名稱在業務層已正規化為小寫，此處直接等值比對，可完整使用 uk_tags_name。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_list_by_tag`$$
CREATE PROCEDURE `sp_post_list_by_tag`(
    IN p_viewer_id         BIGINT,
    IN p_tag_name          VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN p_cursor_created_at DATETIME,
    IN p_cursor_post_id    BIGINT,
    IN p_limit             INT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '列出某標籤底下的發文，keyset 分頁，多回傳一筆供判斷是否還有下一頁'
BEGIN
    SET p_limit = LEAST(GREATEST(IFNULL(p_limit, 10), 1), 100);

    SELECT v.`post_id`,
           v.`user_id`,
           v.`content`,
           v.`image`,
           v.`comment_count`,
           v.`like_count`,
           v.`created_at`,
           v.`updated_at`,
           v.`author_name`,
           v.`author_cover_image`,
           v.`author_deleted_at`,
           v.`tag_names`,
           EXISTS (SELECT 1
                     FROM `post_likes` pl
                    WHERE pl.`post_id` = v.`post_id`
                      AND pl.`user_id` = p_viewer_id) AS `liked_by_me`
      FROM `v_post_detail` v
     WHERE v.`post_id` IN (SELECT pt.`post_id`
                             FROM `post_tags` pt
                             INNER JOIN `tags` t ON t.`tag_id` = pt.`tag_id`
                            WHERE t.`name` = p_tag_name)
       AND (p_cursor_created_at IS NULL
            OR v.`created_at` < p_cursor_created_at
            OR (v.`created_at` = p_cursor_created_at AND v.`post_id` < p_cursor_post_id))
     ORDER BY v.`created_at` DESC, v.`post_id` DESC
     LIMIT p_limit;
END$$


-- -----------------------------------------------------------------------------
-- sp_tag_list_popular — 熱門標籤
-- 排除 post_count = 0 的標籤：發文全數刪除後標籤列會留著（供未來重用同一個 tag_id），
-- 但沒有任何內容的標籤不該出現在推薦清單上。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_tag_list_popular`$$
CREATE PROCEDURE `sp_tag_list_popular`(
    IN p_limit INT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '依使用次數列出熱門標籤'
BEGIN
    SET p_limit = LEAST(GREATEST(IFNULL(p_limit, 10), 1), 100);

    SELECT `tag_id`,
           `name`,
           `post_count`
      FROM `tags`
     WHERE `post_count` > 0
     ORDER BY `post_count` DESC, `name` ASC
     LIMIT p_limit;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_find_by_id — 查詢單篇發文
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_find_by_id`$$
CREATE PROCEDURE `sp_post_find_by_id`(
    IN p_viewer_id BIGINT,
    IN p_post_id   BIGINT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '查詢單篇發文，含作者、標籤與觀看者的按讚狀態'
BEGIN
    SELECT v.`post_id`,
           v.`user_id`,
           v.`content`,
           v.`image`,
           v.`comment_count`,
           v.`like_count`,
           v.`created_at`,
           v.`updated_at`,
           v.`author_name`,
           v.`author_cover_image`,
           v.`author_deleted_at`,
           v.`tag_names`,
           EXISTS (SELECT 1
                     FROM `post_likes` pl
                    WHERE pl.`post_id` = v.`post_id`
                      AND pl.`user_id` = p_viewer_id) AS `liked_by_me`
      FROM `v_post_detail` v
     WHERE v.`post_id` = p_post_id;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_update — 編輯發文並重掛標籤【Transaction】
--
-- 授權與 sp_post_delete 相同：以 post_id + user_id 雙重比對，非本人編輯不會異動任何資料。
--
-- 標籤採「先全數卸下、再重新掛上」而非逐一比對差異：內容改寫後標籤集合可能完全不同，
-- 差異比對要處理新增、移除、保留三種情形，而全量替換只有兩句，且結果必然正確。
-- 標籤數量以個位數計，這裡不值得為效能犧牲可讀性。
--
-- 擁有權以 SELECT COUNT(*) 先行確認，而不是沿用 UPDATE 後的 ROW_COUNT()：
-- MySQL 的 ROW_COUNT() 回報的是「實際被改變的列數」而非「符合條件的列數」，
-- 因此使用者若原封不動地再送出一次（例如只想更新標籤、或按了兩次儲存），
-- ROW_COUNT() 會是 0，看起來與「發文不存在或不屬於你」無從區分，
-- 導致一次合法的編輯被回報成 404。先查擁有權即可讓兩種情形分開。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_update`$$
CREATE PROCEDURE `sp_post_update`(
    IN  p_post_id       BIGINT,
    IN  p_user_id       BIGINT,
    IN  p_content       TEXT         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_image         VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN  p_tags_json     TEXT         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    OUT p_affected_rows INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '編輯發文並重掛標籤（僅限本人，含 Transaction），回傳影響筆數'
BEGIN
    DECLARE v_owned INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*) INTO v_owned
      FROM `posts`
     WHERE `post_id` = p_post_id
       AND `user_id` = p_user_id;

    IF v_owned = 0 THEN
        ROLLBACK;
        SET p_affected_rows = 0;
    ELSE
        UPDATE `posts`
           SET `content` = p_content,
               `image`   = p_image
         WHERE `post_id` = p_post_id
           AND `user_id` = p_user_id;

        -- 卸下舊標籤：先遞減使用次數，再移除關聯（順序顛倒就找不到要遞減哪些標籤了）
        UPDATE `tags` t
           SET t.`post_count` = GREATEST(t.`post_count` - 1, 0)
         WHERE t.`tag_id` IN (SELECT pt.`tag_id`
                                FROM `post_tags` pt
                               WHERE pt.`post_id` = p_post_id);

        DELETE FROM `post_tags`
         WHERE `post_id` = p_post_id;

        -- 掛上新標籤，作法與 sp_post_create 相同
        INSERT IGNORE INTO `tags` (`name`)
        SELECT jt.`name`
          FROM JSON_TABLE(
                   CAST(IFNULL(p_tags_json, '[]') AS JSON),
                   '$[*]' COLUMNS (
                       `name` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PATH '$'
                   )
               ) AS jt;

        INSERT IGNORE INTO `post_tags` (`post_id`, `tag_id`)
        SELECT p_post_id, t.`tag_id`
          FROM JSON_TABLE(
                   CAST(IFNULL(p_tags_json, '[]') AS JSON),
                   '$[*]' COLUMNS (
                       `name` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PATH '$'
                   )
               ) AS jt
          INNER JOIN `tags` t ON t.`name` = jt.`name`;

        UPDATE `tags` t
           SET t.`post_count` = t.`post_count` + 1
         WHERE t.`tag_id` IN (SELECT pt.`tag_id`
                                FROM `post_tags` pt
                               WHERE pt.`post_id` = p_post_id);

        COMMIT;
        SET p_affected_rows = 1;
    END IF;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_delete — 刪除發文及其全部附屬資料【Transaction】
--
-- 跨五張資料表：comments、post_likes、post_tags、tags（遞減使用次數）、posts。
-- 外鍵方向要求先清乾淨所有指向這篇發文的列，最後才刪 posts 本身。
-- 若中途失敗，EXIT HANDLER 回滾並以 RESIGNAL 將原始錯誤上拋，
-- 讓應用層取得真實原因而非靜默失敗。
--
-- 越權情境同樣走回滾：非本人刪除時 posts 的影響筆數為 0，此時整筆交易回滾，
-- 連同上面已清除的留言、按讚與標籤關聯一併還原
-- ——因此「刪別人的發文」不會留下任何副作用。這正是本程序必須是單一交易的原因。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_delete`$$
CREATE PROCEDURE `sp_post_delete`(
    IN  p_post_id       BIGINT,
    IN  p_user_id       BIGINT,
    OUT p_affected_rows INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '刪除發文及其留言、按讚與標籤關聯（含 Transaction），回傳影響筆數'
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

    DELETE FROM `post_likes`
     WHERE `post_id` = p_post_id;

    -- 先遞減再移除關聯：順序顛倒就查不到這篇發文用過哪些標籤
    UPDATE `tags` t
       SET t.`post_count` = GREATEST(t.`post_count` - 1, 0)
     WHERE t.`tag_id` IN (SELECT pt.`tag_id`
                            FROM `post_tags` pt
                           WHERE pt.`post_id` = p_post_id);

    DELETE FROM `post_tags`
     WHERE `post_id` = p_post_id;

    DELETE FROM `posts`
     WHERE `post_id` = p_post_id
       AND `user_id` = p_user_id;

    SET v_deleted = ROW_COUNT();

    IF v_deleted = 0 THEN
        -- 發文不存在或不屬於此使用者：還原上方所有已清除的附屬資料
        ROLLBACK;
    ELSE
        COMMIT;
    END IF;

    SET p_affected_rows = v_deleted;
END$$


-- =============================================================================
-- 按讚
-- =============================================================================

-- -----------------------------------------------------------------------------
-- sp_post_like — 按讚【Transaction】
--
-- 跨兩張資料表：post_likes 寫入 + posts.like_count 遞增，必須同進退，
-- 否則畫面上的讚數會與實際按讚的人數對不起來。
--
-- 冪等性由 (post_id, user_id) 複合主鍵搭配 INSERT IGNORE 達成：重複按讚會被主鍵擋下，
-- ROW_COUNT() 為 0，於是不遞增計數。前端的樂觀更新因此可以安心重試，
-- 網路重送也不會把計數灌爆。
--
-- 對不存在的發文按讚時，INSERT IGNORE 會把外鍵錯誤降級為警告而非中止，
-- 因此無法靠例外偵測。改以 OUT p_like_count 回報：查無該發文時為 NULL，
-- 業務層據此回 404。
--
-- updated_at = updated_at 的理由與 sp_comment_create 相同：
-- 按讚不是「編輯發文」，不該更動發文的編輯時間。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_like`$$
CREATE PROCEDURE `sp_post_like`(
    IN  p_post_id    BIGINT,
    IN  p_user_id    BIGINT,
    OUT p_like_count INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '對發文按讚並遞增讚數（含 Transaction、冪等），回傳最新讚數'
BEGIN
    DECLARE v_inserted INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    INSERT IGNORE INTO `post_likes` (`post_id`, `user_id`)
    VALUES (p_post_id, p_user_id);

    SET v_inserted = ROW_COUNT();

    IF v_inserted > 0 THEN
        UPDATE `posts`
           SET `like_count` = `like_count` + 1,
               `updated_at` = `updated_at`
         WHERE `post_id` = p_post_id;
    END IF;

    -- 以聚合函式取值：查無發文時回傳一列 NULL，而非觸發 NOT FOUND 警告
    SELECT MAX(`like_count`) INTO p_like_count
      FROM `posts`
     WHERE `post_id` = p_post_id;

    COMMIT;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_unlike — 取消按讚【Transaction】
-- 與 sp_post_like 對稱，同樣冪等：沒按過讚時 ROW_COUNT() 為 0，計數不動。
-- GREATEST(..., 0) 保證計數不會因任何意外情形變成負數。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_post_unlike`$$
CREATE PROCEDURE `sp_post_unlike`(
    IN  p_post_id    BIGINT,
    IN  p_user_id    BIGINT,
    OUT p_like_count INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '取消按讚並遞減讚數（含 Transaction、冪等），回傳最新讚數'
BEGIN
    DECLARE v_deleted INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    DELETE FROM `post_likes`
     WHERE `post_id` = p_post_id
       AND `user_id` = p_user_id;

    SET v_deleted = ROW_COUNT();

    IF v_deleted > 0 THEN
        UPDATE `posts`
           SET `like_count` = GREATEST(`like_count` - 1, 0),
               `updated_at` = `updated_at`
         WHERE `post_id` = p_post_id;
    END IF;

    SELECT MAX(`like_count`) INTO p_like_count
      FROM `posts`
     WHERE `post_id` = p_post_id;

    COMMIT;
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
           c.`updated_at`,
           u.`user_name`   AS `author_name`,
           u.`cover_image` AS `author_cover_image`,
           u.`deleted_at`  AS `author_deleted_at`
      FROM `comments` c
      INNER JOIN `users` u ON u.`user_id` = c.`user_id`
     WHERE c.`post_id` = p_post_id
     ORDER BY c.`created_at` ASC, c.`comment_id` ASC
     LIMIT p_limit OFFSET p_offset;
END$$


-- -----------------------------------------------------------------------------
-- sp_comment_update — 編輯留言
--
-- 授權方式與 sp_post_update 一致：post_id / comment_id 與 user_id 雙重比對。
-- 擁有權同樣先以 SELECT COUNT(*) 確認，理由見 sp_post_update 的註解
-- （ROW_COUNT() 回報的是實際變更列數，內容未改動時會誤判為無權限）。
--
-- 只異動 comments 一張表：留言的編輯不影響 posts.comment_count，
-- 因此不需要 Transaction。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_comment_update`$$
CREATE PROCEDURE `sp_comment_update`(
    IN  p_comment_id    BIGINT,
    IN  p_user_id       BIGINT,
    IN  p_content       TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    OUT p_affected_rows INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
    COMMENT '編輯留言（僅限本人），回傳影響筆數'
BEGIN
    DECLARE v_owned INT DEFAULT 0;

    SELECT COUNT(*) INTO v_owned
      FROM `comments`
     WHERE `comment_id` = p_comment_id
       AND `user_id`    = p_user_id;

    IF v_owned = 0 THEN
        SET p_affected_rows = 0;
    ELSE
        UPDATE `comments`
           SET `content` = p_content
         WHERE `comment_id` = p_comment_id
           AND `user_id`    = p_user_id;

        SET p_affected_rows = 1;
    END IF;
END$$


-- -----------------------------------------------------------------------------
-- sp_comment_find_by_id — 查詢單則留言
-- 供編輯留言後回傳更新結果使用（PUT 需回傳資源的新狀態）。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_comment_find_by_id`$$
CREATE PROCEDURE `sp_comment_find_by_id`(
    IN p_comment_id BIGINT
)
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT '查詢單則留言，含留言者資訊'
BEGIN
    SELECT c.`comment_id`,
           c.`post_id`,
           c.`user_id`,
           c.`content`,
           c.`created_at`,
           c.`updated_at`,
           u.`user_name`   AS `author_name`,
           u.`cover_image` AS `author_cover_image`,
           u.`deleted_at`  AS `author_deleted_at`
      FROM `comments` c
      INNER JOIN `users` u ON u.`user_id` = c.`user_id`
     WHERE c.`comment_id` = p_comment_id;
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
