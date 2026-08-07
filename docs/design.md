# 設計文件 — 簡易社群媒體平台

本文件說明系統的架構決策、資料庫設計、API 規格與安全性措施，作為實作依據與審核參考。
需求規格見 [`original_spec.md`](../original_spec.md)。

---

## 1. 專案概觀

實作一個簡易社群媒體平台，使用者以**手機號碼**註冊與登入，登入後可發文、編輯與刪除自己的發文，並對任何發文留言。系統採 **Web Server + Application Server + 關聯式資料庫**的三層式架構，所有資料庫存取一律透過 **Stored Procedure**。

### 核心功能

| 功能 | 說明 |
| --- | --- |
| 註冊 | 以手機號碼為唯一帳號識別，密碼加鹽雜湊後儲存 |
| 登入驗證 | 簽發 JWT，未持有效 token 者無法發文或留言 |
| 發文 | 新增、列出全部（分頁）、編輯、刪除；編輯與刪除僅限本人 |
| 留言 | 針對發文新增留言、列出留言、刪除自己的留言 |
| 個人檔案 | 維護使用者名稱、Email、自我介紹、封面照片 |
| 圖片上傳 | 發文圖片與個人封面照，含檔案類型與大小驗證 |

### 規格之外的延伸功能

規格寫明資料表「請包含，但不限制僅能有以下」，介面與功能同樣沒有上限。
以下為在滿足全部規格要求之後補上的部分，選擇的標準是「能否讓這個平台像一個真正在用的產品」：

| 功能 | 說明 | 帶來的技術示範 |
| --- | --- | --- |
| 按讚 | 冪等的按讚 / 取消 | 複合主鍵作為業務約束、`INSERT IGNORE` + `ROW_COUNT()` 的冪等寫法、跨表交易 |
| `#標籤` | 發文時自內容自動解析、標籤瀏覽、熱門標籤 | 跨四張資料表的交易、`JSON_TABLE` 展開陣列、正規化與反正規化計數的並存 |
| 全文搜尋 | 中文關鍵字搜尋 | `FULLTEXT` + ngram 分詞、單字元的 `LIKE` 後援與萬用字元跳脫 |
| 他人個人檔案頁 | 公開檔案 + 發文與留言的合併時間軸 | `UNION ALL` 的寬型別投影、兩張表併成單一時間軸後才分頁 |
| 編輯留言 | 補完留言的 CRUD 對稱性 | `ROW_COUNT()` 陷阱的處理 |
| 修改密碼 / 刪除帳號 | 軟刪除 + 匿名化 | 憑證讀取路徑的收斂、UNIQUE 的釋放與重用、稽核軌跡的保留 |

前端另補上深色模式、無限捲動、樂觀更新與骨架屏，說明見 §8。

---

## 2. 需求對照表

供審核者快速核對規格條目與實作位置。

| 規格要求 | 實作方式 | 對應章節 |
| --- | --- | --- |
| 註冊功能（手機號碼） | `POST /api/auth/register`，`users.phone_number` 設 UNIQUE 約束 | [§6](#6-api-設計) |
| 登入驗證功能 | Spring Security 6 + JWT，寫入操作端點全數需要驗證 | [§7.1](#71-身份驗證與授權) |
| 發文功能（新增/列出/編輯/刪除） | `POST` / `GET` / `PUT` / `DELETE /api/posts` | [§6](#6-api-設計) |
| 留言功能 | `POST` / `GET /api/posts/{postId}/comments` | [§6](#6-api-設計) |
| Web + App + RDBMS 三層架構 | Nginx / Spring Boot / MySQL 8，三個獨立容器 | [§3](#3-系統架構) |
| 展示層、業務層、資料層、共用層 | `presentation` / `business` / `data` / `common` 四個頂層套件 | [§4](#4-後端分層設計) |
| Vue.js 前端 | Vue 3 + Vite + TypeScript + Pinia + Tailwind v4 + TanStack Query | [§8](#8-前端設計) |
| Spring Boot 應用程式 | Spring Boot 3.4 / Java 21 | [§3](#3-系統架構) |
| RESTful API | 資源導向路徑、HTTP 動詞語意、標準狀態碼 | [§6](#6-api-設計) |
| Maven 或 Gradle | Maven，附 Maven Wrapper（`mvnw`） | [§3.3](#33-建置與執行) |
| 透過 Stored Procedure 存取資料庫 | 全部經 SP；應用程式 DB 帳號僅授予 `EXECUTE` 權限 | [§5.3](#53-stored-procedure-清單) |
| 多表異動實作 Transaction | 7 支 SP 內含 Transaction；每個跨表動作都收斂成單一支 SP，交易邊界完全落在 SP 內 | [§5.4](#54-transaction-設計) |
| DDL 與 DML 存放於 `\DB` | `DB/01_DDL_schema.sql`、`02_DDL_stored_procedures.sql`、`03_DML_seed_data.sql` | [§5](#5-資料庫設計) |
| 防止 SQL Injection | SP + 參數綁定 + 無動態 SQL + 最小權限 DB 帳號 | [§7.2](#72-sql-injection-防護) |
| 防止 XSS | 輸入清洗 + 輸出自動轉義 + 禁用 `v-html` + CSP 標頭 | [§7.3](#73-xss-防護) |
| 密碼加鹽雜湊 | 獨立 `password_salt` 欄位 + PBKDF2-HMAC-SHA256 | [§7.4](#74-密碼儲存) |
| User / Post / Comment 資料表 | 規格欄位全數實作，另補必要欄位（見 §5.2 說明） | [§5.2](#52-資料表定義) |

---

## 3. 系統架構

### 3.1 三層式部署架構

```mermaid
flowchart TD
    B["瀏覽器"]
    W["Web Server 層 — Nginx<br/>container: web · port 3001<br/>服務 Vue build 靜態檔<br/>服務 /uploads/* 圖片靜態檔（唯讀）<br/>/api/* 反向代理至 app:8081<br/>注入安全標頭 CSP / X-Frame-Options"]
    A["Application Server 層 — Spring Boot 3.4<br/>container: app · port 8081<br/>展示層 / 業務層 / 資料層 / 共用層<br/>JWT 驗證、Bean Validation、Transaction<br/>寫入圖片至 uploads volume"]
    D["資料層 — MySQL 8<br/>container: db · port 3306<br/>僅開放 Stored Procedure 存取<br/>啟動時自動載入 DB/*.sql"]

    B -->|"HTTP :3001"| W
    W -->|"HTTP :8081"| A
    A -->|"JDBC :3306"| D
```

三層各自為獨立容器，透過 Docker Compose 的內部網路溝通。**僅 Nginx 對外暴露連接埠**，Spring Boot 與 MySQL 不對主機開放，符合三層式架構中「前端不直接接觸資料層」的意圖。

圖片以具名 volume `uploads` 共享：Spring Boot 以讀寫模式掛載並負責寫入，Nginx 以**唯讀**模式掛載並負責對外服務。上傳的檔案因此不會經過應用程式的執行路徑被讀出，即使其中混入非預期內容也無法被當作程式執行。

### 3.2 技術選型

| 層級 | 技術 | 選用理由 |
| --- | --- | --- |
| Web Server | Nginx 1.27-alpine | 靜態檔服務效能佳、反向代理設定簡潔、image 體積小 |
| Application Server | Spring Boot 3.4 / Java 21 LTS | Spring Boot 2.7 已於 2023 年終止支援，公開專案不宜採用已停止安全更新的版本 |
| 資料庫 | MySQL 8.4 | Stored Procedure 語法簡潔易讀，JDBC driver 成熟 |
| 資料存取 | Spring JDBC `SimpleJdbcCall` | 見下方說明 |
| 身份驗證 | Spring Security 6 + JWT (jjwt 0.12) | 無狀態驗證，與 RESTful 的無狀態特性一致 |
| 前端 | Vue 3 + TypeScript + Pinia + Tailwind v4 + Reka UI + TanStack Query | 見 §8.1 |
| 建置 | Maven + Maven Wrapper | Wrapper 讓審核者無須預先安裝 Maven |

**為何不用 JPA / Hibernate？**
規格要求所有資料庫存取透過 Stored Procedure，ORM 最主要的價值（自動產生 SQL、關聯映射、快取）在此完全無法發揮，卻要額外維護一套與資料表對應的 Entity。`SimpleJdbcCall` 是 Spring 原生的 SP 呼叫 API，以 `CallableStatement` 綁定參數，程式碼直接對應到 SP 簽章，審核時一眼即可看出每支 SP 如何被呼叫。

### 3.3 建置與執行

```bash
cp .env.example .env     # 填入 JWT_SECRET 等機密設定
docker compose up --build
# 開啟 http://localhost:3001
```

單獨開發時：後端 `cd backend && ./mvnw spring-boot:run`（8081），前端 `cd frontend && npm run dev`（3001，以 `strictPort` 固定，埠被占用即失敗而非自動換埠）。

連接埠一覽：**前端 3001**（唯一對外）、**後端 8081**、資料庫 3306。容器內外使用相同埠號，不做轉換；3001 若與本機其他程式衝突，於 `.env` 調整 `WEB_PORT` 即可，容器內部仍是 3001。

---

## 4. 後端分層設計

規格要求區分展示層、業務層、資料層與共用層，對應為四個頂層套件：

```
com.esun.social
├── presentation/          【展示層】處理 HTTP，不含業務邏輯
│   ├── controller/        AuthController / UserController / PostController
│   │                      CommentController / FileController
│   └── dto/
│       ├── request/       輸入 DTO，套用 Bean Validation 標註
│       └── response/      輸出 DTO，不直接外洩領域模型
│
├── business/              【業務層】業務規則與授權判斷，不認識 HTTP
│   ├── service/           AuthService / UserService / PostService
│   │                      CommentService / FileStorageService
│   └── model/             User / Post / Comment 領域模型
│
├── data/                  【資料層】唯一與資料庫溝通的層級
│   ├── repository/        以 SimpleJdbcCall 呼叫 Stored Procedure
│   └── rowmapper/         ResultSet → 領域模型的映射
│
└── common/                【共用層】跨層共用的基礎設施
    ├── config/            SecurityConfig / JdbcConfig / WebConfig / OpenApiConfig
    ├── security/          JwtTokenProvider / JwtAuthenticationFilter / AuthenticatedUser
    ├── exception/         BusinessException / ErrorCode / GlobalExceptionHandler
    ├── util/              PasswordHasher / HtmlSanitizer
    └── response/          ApiResponse<T> / PageResponse<T>
```

### 依賴方向

```
presentation ──▶ business ──▶ data
      │              │           │
      └──────────────┴───────────┴──▶ common
```

規則為**單向依賴**，且不可逆向：

- 展示層只與業務層對話，不得直接注入 Repository。
- 業務層不得引用任何 `jakarta.servlet` 或 Spring Web 型別，確保業務規則可獨立測試。
- 資料層只回傳領域模型，不回傳 DTO。
- 共用層被所有層引用，但不反向依賴任何一層。

### 各層職責界線

| 層級 | 負責 | 不負責 |
| --- | --- | --- |
| 展示層 | 路由、參數驗證、DTO 轉換、HTTP 狀態碼 | 業務規則、權限判斷、資料庫 |
| 業務層 | 業務規則、授權判斷、Transaction 邊界、輸入清洗 | HTTP 細節、SQL |
| 資料層 | 呼叫 SP、參數綁定、ResultSet 映射 | 業務規則、權限判斷 |
| 共用層 | 設定、安全機制、例外處理、共用工具與回應格式 | 任何特定功能的業務邏輯 |

---

## 5. 資料庫設計

### 5.1 ER 圖

```mermaid
erDiagram
    users ||--o{ posts : "發布"
    users ||--o{ comments : "撰寫"
    posts ||--o{ comments : "被留言"

    users {
        bigint user_id PK "使用者 ID"
        varchar phone_number UK "手機號碼，註冊登入帳號"
        varchar user_name "使用者名稱"
        varchar email "電子郵件"
        varchar password_hash "PBKDF2 雜湊值"
        varchar password_salt "每人獨立隨機鹽"
        varchar cover_image "封面照片路徑，可為空"
        varchar biography "自我介紹，可為空"
        datetime created_at "建立時間"
        datetime updated_at "更新時間"
    }

    posts {
        bigint post_id PK "發文 ID"
        bigint user_id FK "發文者"
        text content "發文內容"
        varchar image "圖片路徑，可為空"
        int comment_count "留言數，反正規化欄位"
        datetime created_at "發佈時間"
        datetime updated_at "更新時間"
    }

    comments {
        bigint comment_id PK "留言 ID"
        bigint user_id FK "留言者"
        bigint post_id FK "所屬發文"
        text content "留言內容"
        datetime created_at "留言時間"
    }
```

### 5.2 資料表定義

規格列出的欄位全數實作，另補三個欄位，理由如下：

| 補充欄位 | 所在資料表 | 理由 |
| --- | --- | --- |
| `phone_number` | `users` | 規格要求「以手機號碼進行註冊與登入」，但欄位清單未列出手機號碼。依規格「請包含，但不限制僅能有以下」補上，並設 UNIQUE 約束確保帳號唯一。 |
| `password_salt` | `users` | 規格要求密碼「加鹽並經雜湊後儲存」，獨立欄位使加鹽機制在 Schema 層面即可驗證。 |
| `comment_count` | `posts` | 反正規化的留言計數。列表頁需顯示留言數，若每次即時 `COUNT(*)` 會在 N 篇發文上產生 N 次額外查詢。此欄位同時使「新增/刪除留言」成為真實的跨資料表異動情境，對應規格的 Transaction 要求。 |

另補 `created_at` / `updated_at` 稽核欄位。

**`users`**

| 欄位 | 型別 | 約束 | 說明 |
| --- | --- | --- | --- |
| `user_id` | `BIGINT` | PK, AUTO_INCREMENT | 使用者 ID |
| `phone_number` | `VARCHAR(20)` | NOT NULL, UNIQUE | 手機號碼，格式 `09XXXXXXXX` |
| `user_name` | `VARCHAR(50)` | NOT NULL | 使用者名稱 |
| `email` | `VARCHAR(255)` | NULL | 電子郵件 |
| `password_hash` | `VARCHAR(255)` | NOT NULL | PBKDF2 雜湊值（Base64） |
| `password_salt` | `VARCHAR(64)` | NOT NULL | 32-byte 隨機鹽（Base64） |
| `cover_image` | `VARCHAR(500)` | NULL | 封面照片相對路徑 |
| `biography` | `VARCHAR(500)` | NULL | 自我介紹 |
| `created_at` | `DATETIME` | NOT NULL, 預設現在時間 | 建立時間 |
| `updated_at` | `DATETIME` | NOT NULL, 更新時自動異動 | 更新時間 |

**`posts`**

| 欄位 | 型別 | 約束 | 說明 |
| --- | --- | --- | --- |
| `post_id` | `BIGINT` | PK, AUTO_INCREMENT | 發文 ID |
| `user_id` | `BIGINT` | NOT NULL, FK → `users` | 發文者 |
| `content` | `TEXT` | NOT NULL | 發文內容 |
| `image` | `VARCHAR(500)` | NULL | 圖片相對路徑 |
| `comment_count` | `INT` | NOT NULL, 預設 0 | 留言數 |
| `created_at` | `DATETIME` | NOT NULL, 預設現在時間 | 發佈時間 |
| `updated_at` | `DATETIME` | NOT NULL, 更新時自動異動 | 更新時間 |

索引：`idx_posts_created_at (created_at DESC)` 供時間軸排序、`idx_posts_user_id (user_id)` 供個人頁查詢。

**`comments`**

| 欄位 | 型別 | 約束 | 說明 |
| --- | --- | --- | --- |
| `comment_id` | `BIGINT` | PK, AUTO_INCREMENT | 留言 ID |
| `user_id` | `BIGINT` | NOT NULL, FK → `users` | 留言者 |
| `post_id` | `BIGINT` | NOT NULL, FK → `posts` | 所屬發文 |
| `content` | `TEXT` | NOT NULL | 留言內容 |
| `created_at` | `DATETIME` | NOT NULL, 預設現在時間 | 留言時間 |

索引：`idx_comments_post_created (post_id, created_at)` 供單篇發文的留言分頁查詢。

字元集統一為 `utf8mb4` / `utf8mb4_unicode_ci`，以正確支援中文與 emoji。

### 5.3 Stored Procedure 清單

全部 25 支，完整參數簽章見 [`DB/README.md`](../DB/README.md#3-stored-procedure)。

| Stored Procedure | 用途 | 含 Transaction |
| --- | --- | --- |
| `sp_user_register` | 註冊：檢查手機是否已存在，寫入使用者，回傳 `OUT` 新 ID | |
| `sp_user_find_by_phone` | 依手機號碼查詢（登入用，回傳雜湊與鹽） | |
| `sp_user_find_credentials_by_id` | 依 ID 取出憑證，供修改密碼時驗證舊密碼 | |
| `sp_user_find_by_id` | 依 ID 查詢使用者 | |
| `sp_user_update_profile` | 更新名稱 / Email / 自我介紹 / 封面照片 | |
| `sp_user_change_password` | 更新密碼雜湊與鹽（鹽一併更換） | |
| `sp_user_soft_delete` | 軟刪除帳號並匿名化身分欄位 | |
| `sp_user_activity_list` | 發文與留言的合併時間軸 | |
| `sp_user_activity_count` | 合併時間軸的總筆數 | |
| `sp_post_create` | 新增發文**並掛上標籤** | ✅ |
| `sp_post_list_cursor` | 時間軸 keyset 分頁，帶出作者、標籤與 `liked_by_me` | |
| `sp_post_search` | 關鍵字全文搜尋，keyset 分頁 | |
| `sp_post_list_by_tag` | 依標籤列出發文，keyset 分頁 | |
| `sp_post_find_by_id` | 查詢單篇發文 | |
| `sp_post_update` | 編輯發文**並重掛標籤**，比對 `user_id` 確保僅能改自己的 | ✅ |
| `sp_post_delete` | 刪除發文**與其留言、按讚、標籤關聯** | ✅ |
| `sp_post_like` | 按讚**並遞增** `posts.like_count`（冪等） | ✅ |
| `sp_post_unlike` | 取消按讚**並遞減** `posts.like_count`（冪等） | ✅ |
| `sp_comment_create` | 新增留言**並遞增** `posts.comment_count` | ✅ |
| `sp_comment_list_by_post` | 分頁列出單篇發文的留言 | |
| `sp_comment_find_by_id` | 查詢單則留言 | |
| `sp_comment_count_by_post` | 回傳單篇發文的留言總數 | |
| `sp_comment_update` | 編輯留言，比對 `user_id` | |
| `sp_comment_delete` | 刪除留言**並遞減** `posts.comment_count` | ✅ |
| `sp_tag_list_popular` | 依使用次數列出熱門標籤 | |

**設計約定**

- 需回傳單一新建 ID 或影響筆數者，一律以 `OUT` 參數回傳，避免呼叫端解析結果集。
- 涉及權限的操作（編輯、刪除）在 SP 內以 `WHERE ... AND user_id = p_user_id` 二次把關，即使業務層的檢查被繞過，資料庫仍拒絕越權異動。
- **SP 內一律不使用動態 SQL**（不出現 `PREPARE` / `EXECUTE`），從根本消除注入路徑。
- 字串參數與**區域變數**都明確標註 `CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`。未標註時定序取自連線（MySQL 8 預設 `utf8mb4_0900_ai_ci`），與資料表欄位比對會直接拋出 `ERROR 1267/1270`。同樣的規則也適用於 `JSON_TABLE` 的欄位與 `UNION` 兩支的字串常值。

**檢視表 `v_post_detail`**：四支發文查詢共用同一份投影（發文 + 作者 + 標籤）。
應用層的 `PostRowMapper` 只有一份，投影就不該有四份；新增欄位時只需改一個地方。
唯一的例外是 `liked_by_me`——它取決於觀看者是誰，屬於呼叫端的參數而非發文的屬性，由各程序以 `EXISTS` 自行附加。

檢視表不影響最小權限原則：`app_user` 對它同樣沒有 `SELECT` 權限，
只有 `SQL SECURITY DEFINER` 的程序讀得到。

### 5.4 Transaction 設計

七支 SP 涉及多資料表異動，需保證原子性：

| 情境 | 涉及資料表 | 不使用 Transaction 的後果 |
| --- | --- | --- |
| 新增發文 | `posts` → `tags` → `post_tags` → `tags.post_count` | 標籤存在卻沒有任何發文用它，或使用次數與實際不符 |
| 編輯發文 | 同上，另含卸下舊標籤 | 舊標籤已遞減但新標籤未掛上，計數永久失準 |
| 刪除發文 | `comments` → `post_likes` → `post_tags` → `tags` → `posts` | 附屬資料已刪但發文刪除失敗，產生孤兒留言與計數失準 |
| 按讚 / 取消 | `post_likes` → `posts.like_count` | 讚已寫入但計數未更新，畫面的讚數與實際人數對不起來 |
| 新增留言 | `comments` → `posts` | 留言已寫入但計數未更新，顯示的留言數與實際不符 |
| 刪除留言 | `comments` → `posts` | 同上，計數虛高 |

SP 內的實作樣式：

```sql
DECLARE EXIT HANDLER FOR SQLEXCEPTION
BEGIN
    ROLLBACK;
    RESIGNAL;
END;

START TRANSACTION;
    DELETE FROM comments   WHERE post_id = p_post_id;
    DELETE FROM post_likes WHERE post_id = p_post_id;
    UPDATE tags t SET t.post_count = GREATEST(t.post_count - 1, 0)
     WHERE t.tag_id IN (SELECT pt.tag_id FROM post_tags pt WHERE pt.post_id = p_post_id);
    DELETE FROM post_tags  WHERE post_id = p_post_id;
    DELETE FROM posts      WHERE post_id = p_post_id AND user_id = p_user_id;
COMMIT;
```

`EXIT HANDLER` 確保任一語句失敗時整筆回滾，`RESIGNAL` 將原始錯誤往上拋，讓應用層能取得真實錯誤原因而非靜默失敗。

**越權情境也走回滾**：非本人刪除時 `posts` 的影響筆數為 0，此時整筆交易 `ROLLBACK`，
連同上面已清除的留言、按讚與標籤關聯一併還原。少了這個回滾，任何人都能對別人的發文
送出刪除請求——刪不掉發文，卻毀了它底下的所有留言。

**業務層刻意不使用 `@Transactional`。** 每一個跨表動作都被設計成單一支 SP，
交易邊界因此完全落在 SP 內。若再由 Spring 開一層外部交易，SP 內的 `COMMIT`
會把外層交易一併提交，反而讓邊界難以推理。

這個決定也反過來約束了功能的實作方式：標籤必須以「業務層解析 → 單一 SP 寫入」的形式完成，
因為若拆成 Java 迴圈呼叫 N 次 SP，每次呼叫都會各自開關交易，原子性蕩然無存。

### 5.5 DB 資料夾結構

規格要求 DDL 與 DML 存放於專案下的 `DB` 資料夾：

| 檔案 | 內容 |
| --- | --- |
| `DB/01_DDL_schema.sql` | 建立資料庫、資料表、索引、外鍵，以及應用程式專用帳號與權限授予 |
| `DB/02_DDL_stored_procedures.sql` | 全部 Stored Procedure 定義 |
| `DB/03_DML_seed_data.sql` | 範例使用者、發文與留言資料 |
| `DB/README.md` | 資料表與 SP 的說明文件 |

檔名的數字前綴用於控制執行順序。這三個檔案會掛載至 MySQL 容器的 `/docker-entrypoint-initdb.d/`，容器首次啟動時依序自動執行，因此**不需要任何手動匯入步驟**。

---

## 6. API 設計

採 RESTful 風格：路徑為名詞資源、以 HTTP 動詞表達操作、回傳標準狀態碼。

| 方法 | 路徑 | 驗證 | 說明 |
| --- | --- | :---: | --- |
| `POST` | `/api/auth/register` | | 註冊 |
| `POST` | `/api/auth/login` | | 登入，回傳 JWT |
| `GET` | `/api/users/me` | 🔒 | 取得自己的完整資料 |
| `PUT` | `/api/users/me` | 🔒 | 更新個人檔案 |
| `GET` | `/api/users/{userId}` | | 取得公開個人檔案 |
| `POST` | `/api/posts` | 🔒 | 新增發文 |
| `GET` | `/api/posts?page=&size=` | | 分頁列出全部發文 |
| `GET` | `/api/posts/{postId}` | | 取得單篇發文 |
| `PUT` | `/api/posts/{postId}` | 🔒 | 編輯發文（僅本人） |
| `DELETE` | `/api/posts/{postId}` | 🔒 | 刪除發文（僅本人） |
| `POST` | `/api/posts/{postId}/comments` | 🔒 | 新增留言 |
| `GET` | `/api/posts/{postId}/comments?page=&size=` | | 分頁列出留言 |
| `DELETE` | `/api/comments/{commentId}` | 🔒 | 刪除留言（僅本人） |
| `POST` | `/api/files/images` | 🔒 | 上傳圖片，回傳存取路徑 |

### 統一回應格式

成功：

```json
{ "success": true, "data": { "postId": 1, "content": "今天天氣很好" }, "error": null }
```

失敗：

```json
{ "success": false, "data": null,
  "error": { "code": "NOT_FOUND", "message": "找不到指定的發文" } }
```

`data` 與 `error` 互斥，未使用的一方序列化時整個省略（`spring.jackson.default-property-inclusion: non_null`），前端可直接以 `success` 分流。錯誤代碼取自 `ErrorCode` enum 的名稱。

分頁資料以 `PageResponse<T>` 包裝，含 `items`、`page`、`size`、`totalElements`、`totalPages`。

### 狀態碼與錯誤處理

| 狀態碼 | 使用情境 |
| --- | --- |
| `200 OK` | 查詢、更新成功 |
| `201 Created` | 註冊、發文、留言、上傳成功 |
| `200 OK`（無 `data`） | 刪除成功。刻意不用 `204`：`204` 依規範不得帶主體，回應就無法沿用全站統一的 `ApiResponse` 外殼，前端得為刪除多寫一條分支 |
| `400 Bad Request` | 輸入驗證失敗 |
| `401 Unauthorized` | 未提供 token、token 無效或已過期 |
| `403 Forbidden` | 已登入但操作他人資源 |
| `404 Not Found` | 資源不存在 |
| `409 Conflict` | 手機號碼已被註冊 |
| `413 Payload Too Large` | 上傳檔案超過大小上限 |

所有例外集中由 `GlobalExceptionHandler` 轉換為統一格式。**對外一律不回傳 stack trace 或資料庫錯誤原文**，避免洩漏內部結構；完整錯誤記錄於伺服器日誌。

---

## 7. 安全設計

### 7.1 身份驗證與授權

- 登入成功後簽發 JWT（HS256），有效期 2 小時，payload 僅含 `userId`、`phoneNumber` 與過期時間，**不含任何敏感資料**。
- 簽章密鑰由環境變數 `JWT_SECRET` 注入，`.env` 已列入 `.gitignore`，**原始碼中不出現任何密鑰**。
- `JwtAuthenticationFilter` 攔截請求，驗證 `Authorization: Bearer <token>` 後填入 Spring Security 的 `SecurityContext`。
- 預設拒絕：`SecurityConfig` 以白名單方式僅開放註冊、登入與讀取類端點，其餘全部需要驗證。
- **授權採雙重檢查**：業務層比對登入者與資源擁有者，資料層的 SP 再以 `WHERE user_id = p_user_id` 把關。單層防護若因程式碼修改而失效，另一層仍能阻擋越權。

### 7.2 SQL Injection 防護

採**四道防線**，任一道失效仍有其他層級保護：

1. **全面使用 Stored Procedure** — 應用程式不組裝任何 SQL 字串。
2. **參數綁定** — `SimpleJdbcCall` 以 `CallableStatement` 傳遞參數，輸入值永遠被當作資料而非可執行語法。
3. **SP 內無動態 SQL** — 不使用 `PREPARE` / `EXECUTE`，杜絕注入在資料庫端被重新組裝的可能。
4. **最小權限資料庫帳號** — 應用程式帳號僅授予 SP 的 `EXECUTE` 權限，**不授予資料表的 SELECT / INSERT / UPDATE / DELETE**。即使前三道全部失效，該帳號在資料庫層面也無法對資料表下達任意 SQL。

連線字串明確設定 `allowMultiQueries=false`，禁止單次請求挾帶多段語句。

第 4 點可由審核者直接驗證：

```bash
docker compose exec db mysql -u app_user -p -e "SELECT * FROM users;"
# 預期結果：ERROR 1142 — SELECT command denied
```

### 7.3 XSS 防護

採**縱深防禦**，輸入、輸出、瀏覽器三個環節各有防線：

1. **輸入清洗** — 業務層以 Jsoup `Safelist.none()` 清洗 `content`、`biography`、`user_name` 與**搜尋關鍵字**，移除全部 HTML 標籤後才寫入資料庫。搜尋關鍵字之所以也要清洗，是因為它會原樣回到搜尋結果頁供顯示——這同樣是一條把使用者輸入送回畫面的路徑。
2. **輸出轉義** — 前端一律使用 `{{ }}` 插值，Vue 會自動轉義；**專案內禁用 `v-html`**，並以 ESLint 規則 `vue/no-v-html` 強制封鎖，使違規在 CI 階段就被擋下而非依賴人工複查。
3. **回應標頭** — API 回應固定 `Content-Type: application/json`，避免瀏覽器誤判為 HTML 而執行內容。
4. **瀏覽器層防護** — Nginx 注入以下標頭：

| 標頭 | 值 | 作用 |
| --- | --- | --- |
| `Content-Security-Policy` | `script-src 'self'`、**`style-src 'self'`**、img / font / connect 皆限同源 | 阻止外部與行內的指令碼**及樣式**執行 |
| `X-Content-Type-Options` | `nosniff` | 阻止 MIME 類型嗅探 |
| `X-Frame-Options` | `DENY` | 防止點擊劫持 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 限制 referrer 外洩 |
| `Permissions-Policy` | 關閉相機、麥克風、定位、付款 | 即使頁面被注入內容，也無法要求這些權限 |

> **實作提醒**：Nginx 的 `add_header` 不會由上層累加——只要子 `location` 自行宣告任何一道 `add_header`，上層的全部標頭都會被丟棄。`frontend/nginx.conf` 因此在每個有自訂標頭的 `location`（`/api/`、`/uploads/`、`/assets/`、`= /index.html`）重複列出所需標頭。這是 Nginx 安全設定最常見的靜默失效點。

**`style-src` 不含 `'unsafe-inline'`。** 這是把前端從 CSS-in-JS 的元件庫改為
Tailwind（建置期輸出靜態 CSS）之後才得以成立的。

**這道政策的實際涵蓋範圍**（值得精確說明，因為很容易被高估）：

| 會被擋下 | 不會被擋下 |
| --- | --- |
| 執行期建立並注入的 `<style>` 元素 | 透過 CSSOM 寫入的樣式（`el.style.x = y`、`style.setProperty`） |
| HTML 原始碼中的 `style="..."` 屬性 | |
| 外部樣式表 | |

CSS-in-JS 的運作方式正是第一項——這就是它當初必須放行 `'unsafe-inline'` 的原因，
也是移除之後真正被關上的門。附帶的好處是，以注入 `<style>` 進行的 CSS 側通道資料竊取同樣被阻斷。

反過來說，CSP **不攔截 CSSOM**，這是規範上的既定行為。Vue 的 `:style` 綁定走的正是 CSSOM
（見 `@vue/runtime-dom` 的 `patchStyle`，它使用 `style.cssText` 與 `style.setProperty`），
因此不會被這道政策擋下。專案仍以 ESLint 禁用 `:style`，但理由是「讓樣式只有一個來源」的一致性，
**不是**安全控制——把它記成 CSP 的配套會讓人對這道防線產生錯誤的信心。

真正必要的配套只有一項：**深色模式的防閃爍腳本放在 `public/theme-init.js`** 這個同源檔案。
它必須在第一次繪製前同步執行，但寫成 `index.html` 裡的 inline script 會被
`script-src 'self'` 擋下；獨立成檔既合規，也不需要為一段程式碼維護 CSP hash。

### 7.4 密碼儲存

- 演算法：**PBKDF2-HMAC-SHA256**，310,000 次迭代（採 OWASP 建議值）。
- 每位使用者產生獨立的 **32-byte 隨機鹽**（`SecureRandom`），以 Base64 存於 `password_salt` 欄位。
- 雜湊結果存於 `password_hash`，**明碼與可逆加密結果均不落地**。
- 登入時以相同鹽重新計算並比對，比對使用**常數時間**方法（`MessageDigest.isEqual`），避免時序攻擊。

> **設計取捨**：業界常見做法是 BCrypt，其鹽值內嵌於雜湊字串中。本專案改採獨立鹽欄位 + PBKDF2，理由是規格明確要求「密碼請加鹽(salt)並經雜湊(Hash)後儲存」，獨立欄位讓加鹽機制在資料表結構上即可被驗證，無須額外說明。PBKDF2 為 NIST 與 OWASP 認可的密碼雜湊函式，在正確的迭代次數下安全強度充分。

### 7.5 檔案上傳防護

| 措施 | 說明 |
| --- | --- |
| 副檔名白名單 | 僅接受 `.jpg` / `.jpeg` / `.png` / `.webp` |
| 內容型別驗證 | 讀取檔案前幾個位元組比對 magic number，不信任用戶端宣告的 MIME |
| 大小上限 | 單檔 5MB，於 Spring 設定與 Nginx `client_max_body_size` 雙重限制 |
| 重新命名 | 以 UUID 產生新檔名，**完全捨棄原始檔名**，杜絕路徑穿越（`../`）與同名覆蓋 |
| 儲存位置 | 獨立 volume，與應用程式碼分離；Nginx 以**唯讀**掛載對外服務，檔案不經過應用程式執行路徑 |

### 7.6 輸入驗證

所有 request DTO 套用 Bean Validation，於展示層即攔截不合規輸入：

| 欄位 | 規則 |
| --- | --- |
| `phoneNumber` | `^09\d{8}$`（台灣手機格式） |
| `password` | 8–100 字元 |
| `userName` | 1–50 字元，不可空白 |
| `email` | 符合 Email 格式、最多 255 字元（選填） |
| 發文 `content` | 1–5000 字元 |
| 留言 `content` | 1–1000 字元 |
| `biography` | 最多 500 字元 |
| `image` / `coverImage` | 須符合 `/uploads/<UUID>.<jpg\|jpeg\|png\|webp>`，即本站上傳端點回傳的格式 |

密碼**不強制**英數混合：長度是密碼強度最有效的槓桿，而複雜度規則會把使用者推向 `Password1!` 這類可預測的變形。實際的防護落在雜湊成本（PBKDF2 310,000 輪）與獨立鹽值上。

驗證失敗由 `GlobalExceptionHandler` 統一轉為 `400`，並列出所有未通過的欄位與原因。

前端以同一組規則（`frontend/src/utils/validation.ts`）在送出前先行提示，但那只是體驗上的及早回饋，**不是安全邊界**——繞過瀏覽器直接呼叫 API 一樣會被後端擋下。

---

## 8. 前端設計

### 8.1 技術組成

| 項目 | 選用 | 選用理由 |
| --- | --- | --- |
| 框架 | Vue 3（Composition API + `<script setup>`） | 規格指定 |
| 語言 | TypeScript | |
| 建置 | Vite | |
| 狀態管理 | Pinia | 僅用於驗證狀態；伺服器資料交給 Query |
| 伺服器狀態 | **TanStack Query** | 見下方 |
| 路由 | Vue Router | |
| 樣式 | **Tailwind v4** | 建置期輸出靜態 CSS，使 CSP 得以收緊（見下方） |
| 無障礙行為 | **Reka UI** | 無樣式的 headless 元件，只提供焦點鎖定、鍵盤操作與 ARIA |
| 圖示 | `@lucide/vue` | |
| HTTP | Axios | 攔截器統一處理權杖附加與 401 |
| 測試 | Vitest + Vue Test Utils | |

**為何不用現成的 UI 元件庫（如 Naive UI）？**

改版前使用 Naive UI，它以 CSS-in-JS 在執行期注入 `<style>`，因此 CSP 的 `style-src`
被迫放行 `'unsafe-inline'`——這等於為了元件庫的實作方式，在最外層的防線上開了一個洞。

改用 Tailwind（建置期產生一份靜態 CSS）+ Reka UI（純行為、無樣式）之後，
執行期沒有任何一行樣式是動態產生的，`style-src` 得以收緊為 `'self'`（見 §7.3）。
代價是基礎元件（按鈕、輸入框、卡片…）需要自己寫，但這些元件本來就很薄；
真正難寫的是對話框與下拉選單的無障礙細節（焦點鎖定、Esc 關閉、焦點歸還、`aria-modal`），
那部分交給 Reka UI，它只負責行為不負責外觀，因此不會把樣式帶回執行期。

**為何引入 TanStack Query？**

改版前每個頁面都自行維護 `loading` / `error` 的 `ref` 並在 `try-catch` 中重抓資料。
這種寫法沒有快取，也沒有失效機制，直接導致幾個具體的體驗問題：
從動態牆點進詳情頁再返回會整頁重抓、刪除發文後要重載整頁使捲動位置跳掉、按讚必須等一個來回才有反應。

Query 把「伺服器狀態」當成一種獨立於元件的東西來管理，因此可以做到：

| 能力 | 本專案的實際用途 |
| --- | --- |
| 快取與失效 | 30 秒內視為新鮮；寫入後以 `queryKey` 的階層前綴一次讓相關列表失效 |
| 樂觀更新 | 按讚立即翻轉圖示與計數、刪除發文立即從列表移除，失敗才回滾 |
| 無限查詢 | `useInfiniteQuery` 搭配後端的游標分頁 |
| 條件式重試 | 只重試 5xx 與網路錯誤；4xx 重試沒有意義 |

`queryKey` 集中定義於 `src/queries/queryKeys.ts`。這是刻意的：key 決定快取如何命中、
以及一次寫入之後該讓哪些查詢失效，若讓字串散落各處，遲早會出現
「新增留言後動態牆的留言數沒更新」這種只在特定操作順序下才浮現的問題。

### 8.2 頁面規劃

| 路由 | 頁面 | 需登入 |
| --- | --- | :---: |
| `/login` | 登入 | |
| `/register` | 註冊 | |
| `/` | 動態牆：發文列表（無限捲動）、新增發文 | |
| `/posts/:postId` | 發文詳情與留言 | |
| `/users/:userId` | 他人的公開檔案 + 合併動態時間軸 | |
| `/search?q=` | 搜尋結果 | |
| `/tags/:name` | 某標籤底下的發文 | |
| `/profile` | 本人的個人檔案編輯 + 自己的動態 | 🔒 |
| `/settings/account` | 帳號設定：修改密碼、刪除帳號 | 🔒 |

Vue Router 的導航守衛負責攔截未登入者對受保護路由的存取。

搜尋的關鍵字放在網址而非元件狀態：搜尋結果應該可以分享、加入書籤、以上一頁返回，
這些都要求它存在於 URL 中。

### 8.3 版面與體驗

- **雙欄 / 單欄**：桌機為「主內容 + 熱門標籤側欄」，手機收成單欄並在底部提供導覽列——
  拇指構不到頂端，主要動作應放在下方。
- **骨架屏**：載入中顯示與真實內容輪廓一致的佔位，而非轉圈圖示。
  形狀對齊之後，資料到達的瞬間不會有版面跳動。
- **圖片**：固定 16:9 容器 + `object-cover` + `loading="lazy"`，
  使圖片載入前後版面高度不變（避免累積版面位移）。
- **深色模式**：以 `.dark` class 切換而非 `@media (prefers-color-scheme)`，
  使用者必須能覆寫系統設定。首屏套用由 `public/theme-init.js` 在第一次繪製前同步完成，
  避免深色使用者看到一瞬間的白底；獨立成檔是為了符合 `script-src 'self'`。
- **鍵盤與無障礙**：skip link、`:focus-visible` 焦點環、圖示按鈕一律有 `aria-label`、
  按讚以 `aria-pressed` 表達狀態、錯誤訊息以 `aria-describedby` 與欄位關聯。
- **草稿保存**：發文內容即時存入 `sessionStorage`，打到一半離開頁面不會消失。
  用 `sessionStorage` 而非 `localStorage`，讓草稿的生命週期與權杖一致。

### 8.4 驗證狀態管理

- JWT 由 Pinia 的 `authStore` 持有，Axios 請求攔截器自動附加 `Authorization` 標頭。
- 回應攔截器統一處理 `401`：清除驗證狀態、**清空 Query 快取**並導向登入頁。
  快取必須一併清除——那些資料是以前一位使用者的身分取得的，
  留著會讓下一位登入者短暫看到不屬於自己的狀態（例如別人的 `likedByMe`）。
- Token 另存一份於 `sessionStorage`，使頁面重整後不需重新登入；**不使用 `localStorage`**，因其在關閉瀏覽器後仍持續存在，於共用電腦上風險較高。`sessionStorage` 於分頁關閉時即失效。

> **設計取捨**：最安全的 token 儲存方式是僅存於記憶體，但頁面一重整就登出，使用體驗不佳。折衷採用 `sessionStorage`，並以 §7.3 的多層 XSS 防護降低 token 被竊取的風險。

---

## 9. 測試策略

| 層級 | 工具 | 涵蓋內容 |
| --- | --- | --- |
| Stored Procedure / 資料層 | Testcontainers + 真實 MySQL 8 | 每支 SP 的正常路徑、邊界條件、**Transaction 回滾行為** |
| 業務層 | JUnit 5 + Mockito | 業務規則、授權判斷、例外情境 |
| 展示層 | `@WebMvcTest` + MockMvc | 狀態碼、輸入驗證、未授權存取被正確拒絕 |
| 安全性 | JUnit 5 | 注入字串與 `<script>` payload 被正確處理 |
| 前端 | Vitest + Vue Test Utils | `authStore` 狀態流轉、表單驗證、主題切換、關鍵元件行為 |

目前規模：後端 **178** 個單元測試 + **94** 個整合測試，前端 **77** 個測試。

新增功能的整合測試特別針對幾個容易寫錯、且錯了不容易發現的地方：

| 測試的行為 | 若沒有測試，會怎麼壞 |
| --- | --- |
| 按讚 / 取消按讚的冪等性 | 重複點擊或網路重送會讓計數持續累加，且無法自我修復 |
| 非本人刪除發文時的完整回滾 | 任何人都能藉刪除請求清空別人發文底下的留言 |
| 編輯發文時標籤的整組替換與計數增減 | `tags.post_count` 永久偏離實際值 |
| 內容未改動時仍回報成功 | 合法的編輯被誤判為 404（`ROW_COUNT()` 陷阱） |
| 軟刪除後手機號碼的釋放與重新註冊 | 使用者刪帳號後永遠無法用原號碼回來 |
| `liked_by_me` 因觀看者而異 | 甲看到乙按的讚顯示成自己按的 |
| 搜尋關鍵字中的萬用字元被跳脫 | 搜尋 `%` 會回傳全站發文 |

另有一項**資料一致性稽核**可隨時執行（見 `DB/README.md`），
比對三個反正規化計數與實際筆數是否相符，作為交易正確性的最終保證。

**為何資料層測試不用 H2？**
H2 的 MySQL 相容模式不支援 MySQL 的 SP 語法，測試將無法涵蓋本專案最核心的部分。改以 Testcontainers 啟動真實 MySQL 8，載入與正式環境**完全相同**的 `DB/*.sql`，確保測試通過即代表 SP 在正式環境也能運作。

> 實作注意：Testcontainers 需以 `withCopyFileToContainer` 將 SQL 檔複製至 `/docker-entrypoint-initdb.d/`，不可使用 `withInitScript` — 後者的腳本解析器無法處理定義 SP 所必需的 `DELIMITER` 指令。

開發遵循測試驅動流程：先寫失敗的測試，再實作至通過，最後重構。

CI（GitHub Actions）於每個 PR 自動執行後端 `./mvnw verify` 與前端 `npm run test && npm run type-check && npm run build`。

---

## 10. 開發流程

專案採分支開發流程，每個功能獨立分支、提交 Pull Request、經人工審核後合併至 `main`。`main` 已設定分支保護規則，禁止直接推送。

| # | 分支 | 內容 |
| --- | --- | --- |
| 1 | `docs/design-spec` | 本設計文件 |
| 2 | `chore/project-scaffold` | Docker Compose、前後端骨架、Nginx 設定、CI |
| 3 | `feat/db-schema` | `DB/` 下的 DDL、Stored Procedure、種子資料 |
| 4 | `feat/backend-foundation` | 四層封裝結構、共用層、Testcontainers 測試基底 |
| 5 | `feat/auth` | 註冊、登入、密碼雜湊、JWT |
| 6 | `feat/post-api` | 發文 CRUD API |
| 7 | `feat/comment-api` | 留言 API 與 Transaction 驗證 |
| 8 | `feat/file-upload` | 圖片上傳與個人檔案 API |
| 9 | `feat/frontend-foundation` | 路由、狀態管理、Axios 攔截器、版面 |
| 10 | `feat/frontend-auth` | 註冊與登入頁 |
| 11 | `feat/frontend-feed` | 動態牆 |
| 12 | `feat/frontend-comment-profile` | 留言介面與個人檔案頁 |
| 13 | `docs/readme-and-hardening` | 安全標頭實裝與完整 README |

---

## 11. 驗收方式

1. `cp .env.example .env` 並填入 `JWT_SECRET`
2. `docker compose up --build`
3. 開啟 `http://localhost:3001`，依序驗證**規格要求的流程**：
   註冊 → 登入 → 發文（含上傳圖片）→ 編輯發文 → 留言 → 刪除發文（確認留言一併消失）→ 編輯個人檔案 → 登出
4. 驗證**延伸功能**：`#標籤` 的解析與瀏覽、按讚的冪等性、他人個人檔案頁的合併時間軸、
   中文搜尋、深色模式的保存與無閃爍、無限捲動、刪除發文後捲動位置不跳動
5. 未登入狀態直接呼叫 `POST /api/posts` 與 `POST /api/posts/{id}/likes`，應回傳 `401`
6. 以他人帳號登入後嘗試編輯不屬於自己的發文與留言，應回傳 `403`
7. 以 `<script>alert(1)</script>` 與 `' OR '1'='1` 作為發文內容**與搜尋關鍵字**送出，
   確認內容被清洗且查詢行為不受影響；搜尋 `%` 應回 0 筆
8. 於帳號設定修改密碼與刪除帳號，確認刪除後無法登入、原手機號碼可重新註冊，
   且過去的發文與留言仍在、作者顯示為「已刪除的使用者」
9. 執行 `docker compose exec db mysql -u app_user -p -e "SELECT * FROM message_me.users;"`，
   應被拒絕（該帳號僅有 `EXECUTE` 權限）
10. `curl -sI http://localhost:3001/ | grep -i content-security-policy`，
    確認 `style-src` 為 `'self'` 且**不含** `'unsafe-inline'`；
    並於瀏覽器 DevTools Console 確認操作全站沒有任何 CSP violation
11. 執行 `DB/README.md` 的資料一致性稽核查詢，三個計數的偏差應全為 `0`
