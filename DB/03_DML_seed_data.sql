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
-- comment_count 的值與下方實際留言筆數一致，模擬正常運作累積出的狀態。
-- =============================================================================
INSERT IGNORE INTO `posts`
    (`post_id`, `user_id`, `content`, `image`, `comment_count`, `created_at`, `updated_at`)
VALUES
    (1, 1, '第一次用這個平台發文，測試一下排版。大家好！', NULL, 2,
     NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),

    (2, 2, '今天爬了硬漢嶺，全程兩小時十分鐘，比上次快了八分鐘。山頂風很大但視野真的值得。', NULL, 1,
     NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),

    (3, 3, '請問有人知道公司樓下那間咖啡店幾點開嗎？每次去都撲空。', NULL, 0,
     NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),

    (4, 1, '咖哩煮太多了，明天早餐也是咖哩，後天可能還是。', NULL, 1,
     NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 5 HOUR);


-- =============================================================================
-- 留言
-- =============================================================================
INSERT IGNORE INTO `comments`
    (`comment_id`, `post_id`, `user_id`, `content`, `created_at`)
VALUES
    (1, 1, 2, '歡迎！排版看起來很正常。', NOW() - INTERVAL 3 DAY + INTERVAL 20 MINUTE),
    (2, 1, 3, '同上，手機上看也沒問題。',   NOW() - INTERVAL 3 DAY + INTERVAL 55 MINUTE),
    (3, 2, 1, '八分鐘很多欸，恭喜。',       NOW() - INTERVAL 2 DAY + INTERVAL 3 HOUR),
    (4, 4, 3, '這就是咖哩的宿命。',         NOW() - INTERVAL 4 HOUR);
