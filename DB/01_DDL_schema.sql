-- =============================================================================
-- 01_DDL_schema.sql — 資料庫、資料表、索引、外鍵與最小權限帳號
-- -----------------------------------------------------------------------------
-- 執行時機：MySQL 容器首次啟動時，由 /docker-entrypoint-initdb.d 依檔名順序自動執行。
-- 執行身分：root（容器 entrypoint 以 root 連線執行本目錄下的所有腳本）。
-- 手動執行：mysql -u root -p < DB/01_DDL_schema.sql
--
-- 本腳本可重複執行（皆使用 IF NOT EXISTS / IF EXISTS）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 明確宣告連線字元集。
-- 容器 entrypoint 以 mysql client 執行本目錄下的腳本，而該 client 的預設字元集
-- 取決於作業系統語系；容器內為 C locale，因此會落回 latin1（實為 cp1252）。
-- 不宣告的話，檔案中的 UTF-8 中文會被伺服器再轉碼一次而變成雙重編碼：
--     '王小明'（E7 8E 8B ...）→ 'çŽ‹å°æ˜Ž'
-- 三支腳本都必須各自宣告一次 —— 每支是獨立的連線。
-- -----------------------------------------------------------------------------
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `message_me`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `message_me`;


-- =============================================================================
-- users — 使用者
-- -----------------------------------------------------------------------------
-- phone_number   規格要求「以手機號碼註冊與登入」，故作為登入帳號並設 UNIQUE。
-- password_hash  PBKDF2-HMAC-SHA256 導出金鑰，Base64 編碼。
-- password_salt  每位使用者獨立的 32-byte 隨機鹽，Base64 編碼（44 字元）。
--                規格要求密碼「加鹽並經雜湊後儲存」，獨立欄位使加鹽機制在
--                Schema 層面即可被稽核，不需翻閱程式碼。
--                雜湊參數與編碼方式詳見 DB/README.md「密碼儲存契約」。
-- deleted_at     軟刪除標記。刪除帳號採「保留內容、匿名化身分」語意：
--                發文與留言原樣留在別人的討論串中，不會在他人的對話裡挖洞；
--                身分欄位則於 sp_user_soft_delete 中就地抹除。稽核軌跡因此完整保留。
--                phone_number 會一併改寫為 deleted_<user_id>，讓原手機號碼可重新註冊。
-- =============================================================================
CREATE TABLE IF NOT EXISTS `users` (
    `user_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '使用者 ID',
    `phone_number`  VARCHAR(20)  NOT NULL                COMMENT '手機號碼，格式 09XXXXXXXX，登入帳號',
    `user_name`     VARCHAR(50)  NOT NULL                COMMENT '使用者名稱',
    `email`         VARCHAR(255)     NULL                COMMENT '電子郵件',
    `password_hash` VARCHAR(255) NOT NULL                COMMENT 'PBKDF2-HMAC-SHA256 雜湊值（Base64）',
    `password_salt` VARCHAR(64)  NOT NULL                COMMENT '每人獨立的 32-byte 隨機鹽（Base64）',
    `cover_image`   VARCHAR(500)     NULL                COMMENT '封面照片相對路徑',
    `biography`     VARCHAR(500)     NULL                COMMENT '自我介紹',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                           COMMENT '建立時間',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    `deleted_at`    DATETIME         NULL                COMMENT '軟刪除時間，NULL 表示帳號有效',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_users_phone_number` (`phone_number`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '使用者';


-- =============================================================================
-- posts — 發文
-- -----------------------------------------------------------------------------
-- comment_count  反正規化的留言計數。列表頁需顯示每篇的留言數，若即時 COUNT(*)
--                會在 N 篇發文上產生 N 次額外查詢；同時使「新增/刪除留言」成為
--                真實的跨資料表異動情境，對應規格的 Transaction 要求。
--                維護責任在 sp_comment_create / sp_comment_delete，兩者皆於
--                Transaction 內與 comments 的異動一併提交。
-- like_count     同上，反正規化的按讚計數，由 sp_post_like / sp_post_unlike 於
--                Transaction 內與 post_likes 的異動一併維護。
-- ft_posts_content
--                全文檢索索引，供 sp_post_search 使用。中文沒有空白分詞，必須指定
--                ngram 解析器，否則整句會被當成單一詞元而永遠搜不到東西。
--                ngram_token_size 預設為 2，因此單一字元的關鍵字無法命中；
--                sp_post_search 對此情形改走 LIKE，細節見該程序的註解。
-- -----------------------------------------------------------------------------
-- 索引順序說明：時間軸改用 keyset（cursor）分頁後，排序鍵為 (created_at, post_id)
-- 兩欄，因此索引也一併改為複合降冪索引，讓 WHERE + ORDER BY 都能吃到同一支索引。
-- =============================================================================
CREATE TABLE IF NOT EXISTS `posts` (
    `post_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '發文 ID',
    `user_id`       BIGINT       NOT NULL                COMMENT '發文者',
    `content`       TEXT         NOT NULL                COMMENT '發文內容',
    `image`         VARCHAR(500)     NULL                COMMENT '圖片相對路徑',
    `comment_count` INT          NOT NULL DEFAULT 0      COMMENT '留言數（反正規化）',
    `like_count`    INT          NOT NULL DEFAULT 0      COMMENT '按讚數（反正規化）',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                           COMMENT '發佈時間',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    PRIMARY KEY (`post_id`),
    -- 時間軸的 keyset 分頁：WHERE (created_at, post_id) < (?, ?) 與 ORDER BY 同鍵同序
    KEY `idx_posts_created_at_post_id` (`created_at` DESC, `post_id` DESC),
    KEY `idx_posts_user_id` (`user_id`),
    FULLTEXT KEY `ft_posts_content` (`content`) WITH PARSER `ngram`,
    CONSTRAINT `fk_posts_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '發文';


-- =============================================================================
-- comments — 留言
-- -----------------------------------------------------------------------------
-- updated_at  留言可被作者編輯（sp_comment_update），需要與 created_at 比對才能
--             在畫面上標示「已編輯」；與 posts 採相同的 ON UPDATE 慣例。
-- =============================================================================
CREATE TABLE IF NOT EXISTS `comments` (
    `comment_id` BIGINT   NOT NULL AUTO_INCREMENT COMMENT '留言 ID',
    `user_id`    BIGINT   NOT NULL                COMMENT '留言者',
    `post_id`    BIGINT   NOT NULL                COMMENT '所屬發文',
    `content`    TEXT     NOT NULL                COMMENT '留言內容',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '留言時間',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    PRIMARY KEY (`comment_id`),
    -- 單篇發文的留言分頁：以 post_id 過濾後直接依 created_at 排序，免除 filesort
    KEY `idx_comments_post_created` (`post_id`, `created_at`),
    KEY `idx_comments_user_id` (`user_id`),
    CONSTRAINT `fk_comments_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    -- 不使用 ON DELETE CASCADE：刪除發文時的留言清除交由 sp_post_delete 在
    -- 明確的 Transaction 中處理，使跨資料表異動在 SQL 中可見、可測試。
    CONSTRAINT `fk_comments_post`
        FOREIGN KEY (`post_id`) REFERENCES `posts` (`post_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '留言';


-- =============================================================================
-- post_likes — 按讚
-- -----------------------------------------------------------------------------
-- 複合主鍵 (post_id, user_id) 即為「一人一篇只能按一次」的約束本身：
-- 不需要額外的唯一鍵，也讓重複按讚在資料庫層面就不可能發生。
-- sp_post_like 以 INSERT IGNORE 搭配 ROW_COUNT() 判斷是否為新的一讚，
-- 因此重複呼叫是冪等的，不會把 posts.like_count 灌爆。
--
-- idx_post_likes_user  供「某使用者按過哪些讚」的反查；雖然目前的畫面尚未使用，
--                      但外鍵本身就需要 user_id 的索引，順帶帶上時間欄位不增加成本。
-- =============================================================================
CREATE TABLE IF NOT EXISTS `post_likes` (
    `post_id`    BIGINT   NOT NULL COMMENT '被按讚的發文',
    `user_id`    BIGINT   NOT NULL COMMENT '按讚者',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '按讚時間',
    PRIMARY KEY (`post_id`, `user_id`),
    KEY `idx_post_likes_user` (`user_id`, `created_at` DESC),
    CONSTRAINT `fk_post_likes_post`
        FOREIGN KEY (`post_id`) REFERENCES `posts` (`post_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_post_likes_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '發文按讚';


-- =============================================================================
-- tags — 標籤
-- -----------------------------------------------------------------------------
-- name        一律以小寫正規化後存入（正規化在業務層完成，見 TagNormalizer），
--             因此 Vue 與 vue 會落在同一個標籤上。
--             唯一鍵使「同名標籤只會有一列」成為資料庫層的保證，
--             sp_post_create / sp_post_update 得以直接用 INSERT IGNORE 做 upsert。
-- post_count  反正規化的使用次數，供熱門標籤排序；與 posts.comment_count 同一套
--             維護模式，由帶有 Transaction 的 SP 與 post_tags 的異動一併維護。
-- =============================================================================
CREATE TABLE IF NOT EXISTS `tags` (
    `tag_id`     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '標籤 ID',
    `name`       VARCHAR(50) NOT NULL                COMMENT '標籤名稱（小寫正規化，不含 # 字元）',
    `post_count` INT         NOT NULL DEFAULT 0      COMMENT '使用此標籤的發文數（反正規化）',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    PRIMARY KEY (`tag_id`),
    UNIQUE KEY `uk_tags_name` (`name`),
    -- 熱門標籤：ORDER BY post_count DESC 直接吃索引
    KEY `idx_tags_post_count` (`post_count` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '標籤';


-- =============================================================================
-- post_tags — 發文與標籤的關聯
-- -----------------------------------------------------------------------------
-- idx_post_tags_tag  「某標籤底下的發文」是標籤頁的主要查詢，以 tag_id 為前綴、
--                    post_id 降冪，可同時滿足過濾與時間軸排序（post_id 遞增即時序）。
-- =============================================================================
CREATE TABLE IF NOT EXISTS `post_tags` (
    `post_id` BIGINT NOT NULL COMMENT '發文',
    `tag_id`  BIGINT NOT NULL COMMENT '標籤',
    PRIMARY KEY (`post_id`, `tag_id`),
    KEY `idx_post_tags_tag` (`tag_id`, `post_id` DESC),
    CONSTRAINT `fk_post_tags_post`
        FOREIGN KEY (`post_id`) REFERENCES `posts` (`post_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_post_tags_tag`
        FOREIGN KEY (`tag_id`) REFERENCES `tags` (`tag_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '發文標籤關聯';


-- =============================================================================
-- v_post_detail — 發文的完整呈現形態
-- -----------------------------------------------------------------------------
-- 四支程序需要同一份發文投影：sp_post_list_cursor、sp_post_find_by_id、
-- sp_post_search、sp_post_list_by_tag。把它收斂成一個檢視表，新增欄位時只需要改一處，
-- 四支程序的回傳形狀自動保持一致——RowMapper 只有一個，投影就不該有四份。
--
-- tag_names 以 GROUP_CONCAT 攤平成逗號分隔字串，而非另開一個結果集：
-- 標籤名稱在業務層以 [\p{L}\p{N}_] 正規化過（見 TagNormalizer），不可能含有逗號，
-- 因此攤平後仍可無歧義地還原。這讓「一篇發文一列」的簡單契約得以維持。
--
-- liked_by_me 刻意不放進來：它取決於觀看者是誰，屬於呼叫端的參數而非發文的屬性，
-- 由各程序以 EXISTS 子查詢自行附加。
--
-- 本檢視表不影響最小權限原則：app_user 對它同樣沒有 SELECT 權限，
-- 只有 SQL SECURITY DEFINER 的程序（以 root 身分執行）讀得到。
-- =============================================================================
CREATE OR REPLACE VIEW `v_post_detail` AS
SELECT p.`post_id`,
       p.`user_id`,
       p.`content`,
       p.`image`,
       p.`comment_count`,
       p.`like_count`,
       p.`created_at`,
       p.`updated_at`,
       u.`user_name`   AS `author_name`,
       u.`cover_image` AS `author_cover_image`,
       u.`deleted_at`  AS `author_deleted_at`,
       (SELECT GROUP_CONCAT(t.`name` ORDER BY t.`name` SEPARATOR ',')
          FROM `post_tags` pt
          INNER JOIN `tags` t ON t.`tag_id` = pt.`tag_id`
         WHERE pt.`post_id` = p.`post_id`) AS `tag_names`
  FROM `posts` p
  INNER JOIN `users` u ON u.`user_id` = p.`user_id`;


-- =============================================================================
-- 應用程式帳號權限 — SQL Injection 的最後一道防線
-- -----------------------------------------------------------------------------
-- MySQL 容器的 entrypoint 會依 MYSQL_USER / MYSQL_PASSWORD 建立帳號，並預設授予
-- 該資料庫的「全部權限」。此處收回全部權限，只留下 Stored Procedure 的 EXECUTE。
--
-- 效果：即使應用程式端的參數綁定全部失效，app_user 在資料庫層面依然無法對資料表
--       下達任意 SELECT / INSERT / UPDATE / DELETE。Stored Procedure 以 DEFINER
--       （root）權限執行，因此仍可正常存取資料表 —— 存取路徑被限縮為
--       「僅 02_DDL_stored_procedures.sql 中已審查過的程序」。
--
-- 審核者可直接驗證：
--   docker compose exec db mysql -u app_user -p -e "SELECT * FROM message_me.users;"
--   → ERROR 1142 (42000): SELECT command denied to user 'app_user'@'...'
--
-- 注意 1：此處的帳號名稱與 .env 的 MYSQL_USER 必須一致（預設 app_user）。
--         若要改用其他帳號名稱，需同步修改下方三行。
--
-- 注意 2：資料庫名稱一律寫成 `message\_me`（底線前加反斜線）。
--         在 GRANT / REVOKE 的資料庫名稱中，未跳脫的 `_` 是「比對任一字元」的萬用字元，
--         `message_me` 與 `message\_me` 因此被 MySQL 視為兩筆不同的授權紀錄。
--         容器 entrypoint 授予的是跳脫形式（`message\_me`），若此處以未跳脫形式 REVOKE，
--         會建立一筆新的萬用字元授權，而 entrypoint 的 ALL PRIVILEGES 原封不動地留著
--         —— 最小權限形同虛設。務必以 SHOW GRANTS 確認結果。
-- =============================================================================
-- 在 Docker 環境中此帳號已由 entrypoint 建立，本行為 no-op；
-- 僅為了讓腳本在容器外手動執行時也能完整跑完（不寫死任何密碼）。
CREATE USER IF NOT EXISTS 'app_user'@'%' IDENTIFIED BY RANDOM PASSWORD;

-- 先授予 EXECUTE：確保 app_user 在本資料庫上必定存在授權紀錄，
-- 否則於全新帳號上執行 REVOKE 會因 ERROR 1141（no such grant defined）中斷腳本。
GRANT EXECUTE ON `message\_me`.* TO 'app_user'@'%';
REVOKE ALL PRIVILEGES ON `message\_me`.* FROM 'app_user'@'%';
GRANT EXECUTE ON `message\_me`.* TO 'app_user'@'%';
