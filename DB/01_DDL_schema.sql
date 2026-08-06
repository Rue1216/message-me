-- =============================================================================
-- 01_DDL_schema.sql — 資料庫、資料表、索引、外鍵與最小權限帳號
-- -----------------------------------------------------------------------------
-- 執行時機：MySQL 容器首次啟動時，由 /docker-entrypoint-initdb.d 依檔名順序自動執行。
-- 執行身分：root（容器 entrypoint 以 root 連線執行本目錄下的所有腳本）。
-- 手動執行：mysql -u root -p < DB/01_DDL_schema.sql
--
-- 本腳本可重複執行（皆使用 IF NOT EXISTS / IF EXISTS）。
-- =============================================================================

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
-- =============================================================================
CREATE TABLE IF NOT EXISTS `posts` (
    `post_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '發文 ID',
    `user_id`       BIGINT       NOT NULL                COMMENT '發文者',
    `content`       TEXT         NOT NULL                COMMENT '發文內容',
    `image`         VARCHAR(500)     NULL                COMMENT '圖片相對路徑',
    `comment_count` INT          NOT NULL DEFAULT 0      COMMENT '留言數（反正規化）',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                           COMMENT '發佈時間',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    PRIMARY KEY (`post_id`),
    -- 時間軸為預設排序，降冪索引可直接滿足 ORDER BY created_at DESC
    KEY `idx_posts_created_at` (`created_at` DESC),
    KEY `idx_posts_user_id` (`user_id`),
    CONSTRAINT `fk_posts_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '發文';


-- =============================================================================
-- comments — 留言
-- =============================================================================
CREATE TABLE IF NOT EXISTS `comments` (
    `comment_id` BIGINT   NOT NULL AUTO_INCREMENT COMMENT '留言 ID',
    `user_id`    BIGINT   NOT NULL                COMMENT '留言者',
    `post_id`    BIGINT   NOT NULL                COMMENT '所屬發文',
    `content`    TEXT     NOT NULL                COMMENT '留言內容',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '留言時間',
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
-- 應用程式帳號權限 — SQL Injection 的最後一道防線
-- -----------------------------------------------------------------------------
-- MySQL 容器的 entrypoint 會依 MYSQL_USER / MYSQL_PASSWORD 建立帳號，並預設授予
-- 該資料庫的「全部權限」。此處收回全部權限，只留下 Stored Procedure 的 EXECUTE。
--
-- 效果：即使應用程式端的參數綁定全部失效，app_user 在資料庫層面依然無法對資料表
--       下達任意 SELECT / INSERT / UPDATE / DELETE。Stored Procedure 以 DEFINER
--       （root）權限執行，因此仍可正常存取資料表 —— 存取路徑被限縮為「僅這 14 支
--       已審查過的程序」。
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
