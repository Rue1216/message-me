-- =============================================================================
-- 03_DML_seed_data.sql — 範例資料
-- -----------------------------------------------------------------------------
-- 目的：讓審核者 docker compose up 之後不必先註冊即可看到動態牆內容，
--       並可直接以下方帳號登入驗證各項功能。
--
-- 全部使用 INSERT IGNORE 搭配明確主鍵，重複執行為 no-op，不會產生重複資料，
-- 也不會覆寫使用者後續建立的內容。
--
-- ⚠ 這是評測用的示範資料，正式環境請勿載入。
-- =============================================================================

-- 連線字元集，理由見 01_DDL_schema.sql 的同名段落；不宣告會使示範資料中的中文雙重編碼。
SET NAMES utf8mb4;

USE `message_me`;


-- =============================================================================
-- 使用者 — 三位示範使用者
-- -----------------------------------------------------------------------------
-- 三位的密碼皆為 Test1234!
--
-- password_hash / password_salt 是實際以下列參數算出的真值，並非佔位字串，
-- 因此可直接用於登入驗證：
--     salt = 32 bytes 隨機值，Base64 編碼（每人獨立）
--     hash = Base64( PBKDF2WithHmacSHA256(password, Base64Decode(salt), 310000, 256 bits) )
-- 完整契約見 DB/README.md「密碼儲存契約」，應用層的實作必須與此一致。
-- =============================================================================
INSERT IGNORE INTO `users`
    (`user_id`, `phone_number`, `user_name`, `email`, `password_hash`, `password_salt`, `biography`, `created_at`, `updated_at`)
VALUES
    (1, '0912345678', '王小明', 'xiaoming@example.com',
     '5rj8GO8PnCh8FCkNRyl6Y9s4T/NARLuDhx+/CYI7A0A=',
     'TbKyTKQvxnX97ehfRS2efDLVA3nc6NlH9yWduLHMtGE=',
     '喜歡在下班後煮一鍋咖哩，然後拍照忘記吃。',
     NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 30 DAY),

    (2, '0922333444', '陳美玲', 'meiling@example.com',
     'pXLrR7ttjsglfSTckTmNcnwBUMACwbHdbFFT+ygPg/g=',
     'eOZ/oCNblGkNxusk8gbLuUpwa1JdtEvxUSCTntKFuQM=',
     '週末登山，平日爬樓梯。',
     NOW() - INTERVAL 21 DAY, NOW() - INTERVAL 21 DAY),

    (3, '0933555666', '林大衛', 'david@example.com',
     'WwzPQVOJ0o27hV9pPFvHN5vpw1r5Ur8GkQYd3Pnfcw8=',
     'Msqln9JtlEN88nh00A7EiUi4RiAH/2ygTRavO4a3RVM=',
     NULL,
     NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 14 DAY);


-- =============================================================================
-- 發文
-- -----------------------------------------------------------------------------
-- comment_count 與 like_count 的值均與下方實際資料一致，模擬正常運作累積出的狀態。
-- 標籤不寫在內文裡：它由發文者在標籤欄位指定，對應到下方的 post_tags 關聯。
-- 內文中若出現 # 也只是一般字元，不會產生標籤。
-- =============================================================================
INSERT IGNORE INTO `posts`
    (`post_id`, `user_id`, `content`, `image`, `comment_count`, `like_count`, `created_at`, `updated_at`)
VALUES
    (1, 1, '第一次用這個平台發文，測試一下排版。大家好！', NULL, 2, 2,
     NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),

    (2, 2, '今天爬了硬漢嶺，全程兩小時十分鐘，比上次快了八分鐘。山頂風很大但視野真的值得。', NULL, 1, 1,
     NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),

    (3, 3, '請問有人知道公司樓下那間咖啡店幾點開嗎？每次去都撲空。', NULL, 0, 0,
     NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),

    (4, 1, '咖哩煮太多了，明天早餐也是咖哩，後天可能還是。', NULL, 1, 2,
     NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 5 HOUR);


-- =============================================================================
-- 留言
-- -----------------------------------------------------------------------------
-- updated_at 明確等於 created_at：該欄位設有 ON UPDATE CURRENT_TIMESTAMP 與
-- DEFAULT CURRENT_TIMESTAMP，若省略不寫會取當下時間，使每一則留言在畫面上
-- 都被標示成「已編輯」。
-- =============================================================================
INSERT IGNORE INTO `comments`
    (`comment_id`, `post_id`, `user_id`, `content`, `created_at`, `updated_at`)
VALUES
    (1, 1, 2, '歡迎！排版看起來很正常。', NOW() - INTERVAL 3 DAY + INTERVAL 20 MINUTE,
                                          NOW() - INTERVAL 3 DAY + INTERVAL 20 MINUTE),
    (2, 1, 3, '同上，手機上看也沒問題。',   NOW() - INTERVAL 3 DAY + INTERVAL 55 MINUTE,
                                          NOW() - INTERVAL 3 DAY + INTERVAL 55 MINUTE),
    (3, 2, 1, '八分鐘很多欸，恭喜。',       NOW() - INTERVAL 2 DAY + INTERVAL 3 HOUR,
                                          NOW() - INTERVAL 2 DAY + INTERVAL 3 HOUR),
    (4, 4, 3, '這就是咖哩的宿命。',         NOW() - INTERVAL 4 HOUR,
                                          NOW() - INTERVAL 4 HOUR);


-- =============================================================================
-- 按讚
-- -----------------------------------------------------------------------------
-- 與上方 posts.like_count 一致：發文 1 兩讚、發文 2 一讚、發文 3 無讚、發文 4 兩讚。
-- 審核者可據此驗證反正規化欄位是否失準：
--     SELECT p.post_id, p.like_count, COUNT(pl.user_id)
--       FROM posts p LEFT JOIN post_likes pl ON pl.post_id = p.post_id
--      GROUP BY p.post_id, p.like_count;
-- =============================================================================
INSERT IGNORE INTO `post_likes` (`post_id`, `user_id`, `created_at`)
VALUES
    (1, 2, NOW() - INTERVAL 3 DAY + INTERVAL 25 MINUTE),
    (1, 3, NOW() - INTERVAL 3 DAY + INTERVAL 60 MINUTE),
    (2, 1, NOW() - INTERVAL 2 DAY + INTERVAL 3 HOUR),
    (4, 2, NOW() - INTERVAL 4 HOUR),
    (4, 3, NOW() - INTERVAL 3 HOUR);


-- =============================================================================
-- 標籤
-- -----------------------------------------------------------------------------
-- post_count 與下方 post_tags 的關聯數一致。標籤名稱不含 # 字元，且一律小寫
-- （中文無大小寫之分，此處與 TagNormalizer 的正規化規則一致即可）。
-- =============================================================================
INSERT IGNORE INTO `tags` (`tag_id`, `name`, `post_count`, `created_at`)
VALUES
    (1, '新手上路', 1, NOW() - INTERVAL 3 DAY),
    (2, '登山',     1, NOW() - INTERVAL 2 DAY),
    (3, '硬漢嶺',   1, NOW() - INTERVAL 2 DAY),
    (4, '咖啡',     1, NOW() - INTERVAL 1 DAY),
    (5, '料理',     1, NOW() - INTERVAL 5 HOUR),
    (6, '咖哩',     1, NOW() - INTERVAL 5 HOUR);


INSERT IGNORE INTO `post_tags` (`post_id`, `tag_id`)
VALUES
    (1, 1),
    (2, 2), (2, 3),
    (3, 4),
    (4, 5), (4, 6);
