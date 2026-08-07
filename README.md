# Message Me — 簡易社群媒體平台

以三層式架構實作的社群媒體平台，提供註冊、登入驗證、發文 CRUD 與留言功能，
並在規格之外補上按讚、標籤、全文搜尋、個人動態時間軸與帳號生命週期管理。
所有資料庫存取一律透過 Stored Procedure，跨表異動以 Transaction 保護。

本專案為技術評測作業，需求規格見 [`original_spec.md`](original_spec.md)，
架構決策與取捨的完整說明見 [設計文件](docs/design.md)。

| 規格要求的功能 | 額外實作的功能 |
| --- | --- |
| 註冊 / 登入驗證 | 修改密碼、刪除帳號（軟刪除 + 匿名化） |
| 發文 CRUD | 按讚（冪等）、`#標籤` 自動解析、標籤瀏覽 |
| 留言 | 編輯留言 |
| 個人檔案 | 他人的公開檔案頁、發文與留言的合併動態時間軸 |
| — | 中文全文搜尋（ngram）、熱門標籤 |
| — | 深色模式、無限捲動、樂觀更新、骨架屏 |

---

## 快速啟動

需求：Docker Desktop（含 Docker Compose）。

```bash
cp .env.example .env          # 填入 JWT_SECRET 與資料庫密碼
docker compose up --build     # 首次啟動會建置前後端映像檔並載入 DB/*.sql
# 開啟 http://localhost:3001
```

`JWT_SECRET` 請填入至少 32 位元組的隨機字串，例如 `openssl rand -base64 48`。

種子資料（`DB/03_DML_seed_data.sql`）已建立三位示範使用者，密碼皆為 `Test1234!`：

| 手機號碼 | 名稱 |
| --- | --- |
| `0912345678` | 王小明 |
| `0922333444` | 陳美玲 |
| `0933555666` | 林大衛 |

停止與清除：

```bash
docker compose down           # 停止容器，保留資料
docker compose down -v        # 連同資料庫與上傳圖片的 volume 一併清除
```

---

## 連接埠

| 用途 | 連接埠 | 對外開放 | 說明 |
| --- | :---: | :---: | --- |
| **前端**（Nginx，Web Server 層） | **3001** | ✅ | 唯一的入口，`http://localhost:3001`。容器內外皆為 3001，不做埠號轉換 |
| **後端**（Spring Boot，Application Server 層） | **8081** | ❌ | 只在容器網路內；前端經 `/api/*` 反向代理至 `app:8081` |
| 資料庫（MySQL） | 3306 | ❌ | 只在容器網路內 |

`docker compose` 啟動時，**只有 3001 會占用本機連接埠**。若 3001 已被其他程式使用，於 `.env` 調整 `WEB_PORT`（例如 `WEB_PORT=3002`），容器內部仍是 3001。

本機不透過容器開發時，兩個服務直接占用本機連接埠：`npm run dev` 固定綁 **3001**（埠被占用會直接失敗而不會換埠），`./mvnw spring-boot:run` 綁 **8081**。網址與容器環境一致，兩種啟動方式不需要記兩組。

---

## 架構

```
       Browser
          │  http://localhost:3001
          ▼
┌─────────────────────────┐
│  Nginx  (Web Server 層) │  靜態檔：Vue build 產物
│  container: web         │  反向代理：/api/* → app:8081
│                         │  安全標頭：CSP / X-Frame-Options / nosniff …
└───────────┬─────────────┘
            │  http://app:8081
            ▼
┌─────────────────────────┐
│ Spring Boot             │  展示層 / 業務層 / 資料層 / 共用層
│ (Application Server 層) │  JWT 驗證、Bean Validation、標籤解析
│ container: app          │  volume: /app/uploads（圖片）
└───────────┬─────────────┘
            │  jdbc:mysql://db:3306
            ▼
┌─────────────────────────┐
│ MySQL 8  (資料層)       │  僅透過 Stored Procedure 存取
│ container: db           │  初始化：./DB/*.sql 掛載至 initdb.d
└─────────────────────────┘
```

只有 `web` 對外開放連接埠；`app` 與 `db` 僅存在於容器網路內。

| 層級 | 技術 |
| --- | --- |
| Web Server | Nginx 1.27（靜態檔服務 + API 反向代理 + 安全標頭） |
| Application Server | Spring Boot 3.4 / Java 21 / Spring Security 6 + JWT |
| 資料層 | MySQL 8.4，存取一律透過 Stored Procedure（Spring JDBC `SimpleJdbcCall`） |
| 前端 | Vue 3 + Vite + TypeScript + Pinia + Vue Router + **Tailwind v4** + **Reka UI** + **TanStack Query** + Axios |
| 建置 | Maven Wrapper（後端）、npm（前端） |
| 測試 | JUnit 5 / Mockito / MockMvc / Testcontainers、Vitest / Vue Test Utils |

前端不使用執行期產生樣式的元件庫（例如 CSS-in-JS）。Tailwind 於建置期輸出單一份靜態 CSS，
Reka UI 只提供無樣式的無障礙行為（焦點鎖定、鍵盤操作、ARIA），
因此 CSP 的 `style-src` 得以收緊為 `'self'`——詳見下方「安全性」。

### 專案結構

```
esun-hw/
├── DB/                       # 規格指定位置：DDL、Stored Procedure、種子資料
├── backend/                  # Spring Boot（presentation / business / data / common）
├── frontend/                 # Vue 3 + Vite，含 nginx.conf
├── docs/design.md            # 設計文件
├── docker-compose.yml
└── .env.example
```

前端原始碼依領域分層，測試集中於各模組的 `__tests__/`：

```
frontend/src/
├── api/          client/（Axios 實例）、resources/（各資源的端點）
├── components/   ui/（基礎元件）、layout/、post/、comment/、user/、tag/、common/
├── composables/  usePostFeed、useLikePost、useTheme、useInfiniteScroll…
├── queries/      queryKeys（快取鍵的單一來源）、queryClient
├── stores/       auth
├── utils/        format/、validation/
└── views/        路由層級頁面，只負責組合
```

---

## API

所有端點位於 `/api` 之下。🔒 表示需要 `Authorization: Bearer <accessToken>`。

| 方法 | 路徑 | 驗證 | 說明 |
| --- | --- | :---: | --- |
| `POST` | `/api/auth/register` | | 註冊，回 `201` |
| `POST` | `/api/auth/login` | | 登入，回傳 JWT 與個人檔案 |
| `GET` | `/api/users/me` | 🔒 | 取得本人完整資料（含手機、Email） |
| `PUT` | `/api/users/me` | 🔒 | 更新個人檔案（全欄位取代） |
| `PUT` | `/api/users/me/password` | 🔒 | 修改密碼（需提供目前密碼） |
| `DELETE` | `/api/users/me` | 🔒 | 刪除帳號（軟刪除 + 匿名化，需提供密碼） |
| `GET` | `/api/users/{userId}` | | 他人的公開檔案（不含手機、Email） |
| `GET` | `/api/users/{userId}/activities?page=&size=` | | 發文與留言的合併時間軸，新到舊 |
| `POST` | `/api/posts` | 🔒 | 新增發文，回 `201`；內容中的 `#標籤` 自動解析 |
| `GET` | `/api/posts?cursor=&size=` | | 動態牆，新到舊，**游標分頁** |
| `GET` | `/api/posts/search?q=&cursor=&size=` | | 全文搜尋，游標分頁 |
| `GET` | `/api/posts/{postId}` | | 單篇發文 |
| `PUT` | `/api/posts/{postId}` | 🔒 | 編輯發文（僅本人），標籤依新內容重新解析 |
| `DELETE` | `/api/posts/{postId}` | 🔒 | 刪除發文（僅本人，連帶刪除留言、按讚、標籤關聯） |
| `POST` | `/api/posts/{postId}/likes` | 🔒 | 按讚（冪等） |
| `DELETE` | `/api/posts/{postId}/likes` | 🔒 | 取消按讚（冪等） |
| `POST` | `/api/posts/{postId}/comments` | 🔒 | 新增留言，回 `201` |
| `GET` | `/api/posts/{postId}/comments?page=&size=` | | 留言分頁，舊到新 |
| `PUT` | `/api/comments/{commentId}` | 🔒 | 編輯留言（僅本人） |
| `DELETE` | `/api/comments/{commentId}` | 🔒 | 刪除留言（僅本人） |
| `GET` | `/api/tags/popular?limit=` | | 熱門標籤，依使用次數排序 |
| `GET` | `/api/tags/{name}/posts?cursor=&size=` | | 依標籤列出發文，游標分頁 |
| `POST` | `/api/files/images` | 🔒 | 上傳圖片（`multipart/form-data`，欄位名 `file`） |
| `GET` | `/api/health` | | 健康檢查 |

### 兩種分頁

| 方式 | 用於 | 回應形狀 |
| --- | --- | --- |
| **游標（keyset）** | 動態牆、搜尋、標籤 | `{ items, nextCursor, hasMore }` |
| **頁碼（offset）** | 留言、個人動態時間軸 | `{ items, page, size, totalElements, totalPages }` |

時間軸類的列表改用游標，是因為動態牆會持續有新內容插到最前面：
offset 分頁在這種資料上必然出錯——讀第 1 頁的同時有人發了文，第 2 頁的起點就整體位移一筆，
於是同一則發文出現兩次。游標以「上一頁最後一筆的位置」為界，不受插入影響。

`cursor` 對前端不透明（Base64 編碼的 `建立時間|主鍵`），把上一次回應的 `nextCursor` 原樣帶回即可；
留空代表第一頁。留言與個人動態的資料量受單篇 / 單人所限，且需要顯示總頁數，因此保留頁碼分頁。

頁碼 `page` 自 1 起算，`size` 上限 100。

### 回應格式

成功：

```json
{ "success": true, "data": { "postId": 1, "content": "今天天氣很好" } }
```

失敗：

```json
{ "success": false, "error": { "code": "FORBIDDEN", "message": "沒有權限執行這項操作" } }
```

`data` 與 `error` 互斥，未使用的一方不會出現在 JSON 中。`error.code` 是穩定的錯誤代碼
（前端據以分支），`error.message` 僅供顯示。分頁資料再包一層——
游標分頁為 `{ items, nextCursor, hasMore }`，頁碼分頁為
`{ items, page, size, totalElements, totalPages }`（見上方「兩種分頁」）。

錯誤代碼與 HTTP 狀態碼定義於 `common/exception/ErrorCode.java`：
`VALIDATION_ERROR` `400`、`UNAUTHORIZED` / `INVALID_CREDENTIALS` `401`、`FORBIDDEN` `403`、
`NOT_FOUND` `404`、`PHONE_ALREADY_REGISTERED` `409`、`PAYLOAD_TOO_LARGE` `413`、
`UNSUPPORTED_MEDIA_TYPE` `415`、`INTERNAL_ERROR` `500`。

### 試打

```bash
# 登入取得權杖
TOKEN=$(curl -s -X POST http://localhost:3001/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"0912345678","password":"Test1234!"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

# 發文
curl -X POST http://localhost:3001/api/posts \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"content":"第一則發文"}'

# 未帶權杖 → 401
curl -i -X POST http://localhost:3001/api/posts \
  -H 'Content-Type: application/json' -d '{"content":"沒有登入"}'
```

---

## 資料庫

六張資料表 `users` / `posts` / `comments` / `post_likes` / `tags` / `post_tags`，
一個檢視表 `v_post_detail`，25 支 Stored Procedure。欄位與索引說明見
[`DB/README.md`](DB/README.md)。應用程式**沒有**資料表的 CRUD 權限，只被授予 SP 的 `EXECUTE`。

跨表異動由 SP 內的 Transaction 保護，並搭配 `DECLARE EXIT HANDLER FOR SQLEXCEPTION`
做 `ROLLBACK` 與 `RESIGNAL`：

| Stored Procedure | 跨表異動 |
| --- | --- |
| `sp_post_create` | 寫入發文 + `tags` upsert + `post_tags` 關聯 + 遞增 `tags.post_count`（**跨四表**） |
| `sp_post_update` | 同上，另需先卸下舊標籤並遞減其使用次數 |
| `sp_post_delete` | 刪除留言 + 按讚 + 標籤關聯 + 遞減 `tags.post_count` + 刪除發文（**跨五表**） |
| `sp_post_like` | 寫入 `post_likes` + 遞增 `posts.like_count` |
| `sp_post_unlike` | 刪除 `post_likes` + 遞減 `posts.like_count` |
| `sp_comment_create` | 新增留言 + 遞增 `posts.comment_count` |
| `sp_comment_delete` | 刪除留言 + 遞減 `posts.comment_count` |

`sp_post_delete` 的回滾是最關鍵的一支：它「先清附屬資料、最後刪發文」，
若刪發文時因為不是本人而影響 0 列，前面已刪除的留言與按讚就必須全部還原。
少了這個回滾，任何人都能對別人的發文送出刪除請求——刪不掉發文，卻毀了它底下的所有留言。

### 標籤的解析位置

`#標籤` 由**業務層**（`TagExtractor`）以正則自內容解析後，以 JSON 陣列傳入 SP，
SP 內再以 MySQL 8 的 `JSON_TABLE` 展開成資料列。規格要求的是「透過 Stored Procedure 存取資料庫」，
不是「用 SQL 做字串處理」——在 SP 裡以 `WHILE` 搭配 `SUBSTRING_INDEX` 手工切字串，
只會換來一段難讀且無法單獨測試的迴圈。寫入時仍是單一 SP 呼叫，跨表交易的完整性不受影響。

### 反正規化計數的一致性

`posts.comment_count`、`posts.like_count`、`tags.post_count` 皆為反正規化欄位，
維護責任完全在上述帶交易的 SP 中。審核者可隨時稽核是否失準（三欄應全為 `0`）：

```bash
docker compose exec db mysql -u root -p message_me -e "
SELECT
 (SELECT COUNT(*) FROM posts p WHERE p.comment_count <> (SELECT COUNT(*) FROM comments c WHERE c.post_id=p.post_id)) AS comment_drift,
 (SELECT COUNT(*) FROM posts p WHERE p.like_count    <> (SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.post_id)) AS like_drift,
 (SELECT COUNT(*) FROM tags  t WHERE t.post_count    <> (SELECT COUNT(*) FROM post_tags pt WHERE pt.tag_id=t.tag_id)) AS tag_drift;"
```

### 中文全文搜尋

`posts.content` 建有 `FULLTEXT ... WITH PARSER ngram`。中文沒有以空白分隔的詞界，
預設解析器會把整段內容視為單一詞元，導致任何查詢都搜不到東西；ngram 將內容切成固定長度的字元組才使其成立。

`ngram_token_size` 預設為 2，因此**單一字元**的關鍵字不會產生任何詞元。
`sp_post_search` 對此情形改走 `LIKE`（並先跳脫 `%` `_` `\`），
讓使用者搜「山」時仍有結果，而不是拿到一個空畫面。

---

## 安全性

| 面向 | 措施 |
| --- | --- |
| SQL Injection | 全面使用 Stored Procedure、`CallableStatement` 參數綁定、SP 內無動態 SQL、應用程式帳號僅有 `EXECUTE` 權限、連線字串 `allowMultiQueries=false` |
| XSS | 輸入端以 Jsoup `Safelist.none()` 清洗（含**搜尋關鍵字**，它會回到畫面上）、輸出端一律 `{{ }}` 自動轉義並以 ESLint `vue/no-v-html` 封鎖 `v-html`、Nginx 注入 CSP 等安全標頭 |
| 密碼 | PBKDF2-HMAC-SHA256（310,000 輪）+ 每人獨立 32-byte 隨機鹽，存於 `password_salt` 欄位；比對使用常數時間方法 |
| 身分驗證 | 無狀態 JWT（HS256），密鑰由環境變數注入；預設拒絕，僅白名單端點公開 |
| 授權 | 業務層與 SP 雙重比對 `user_id`，單層失效仍擋得住越權 |
| 檔案上傳 | magic number 嗅探（不信任 MIME 與副檔名）、5MB 上限、UUID 重新命名、寫入前驗證落點、Nginx 以唯讀且 `sandbox` 政策提供 |
| 權杖保存 | 前端存於 `sessionStorage` 而非 `localStorage`，分頁關閉即失效 |
| 帳號變更 | 修改密碼與刪除帳號都必須再次提供密碼——權杖可能外洩，「持有權杖」與「知道密碼」應是兩道獨立的關卡 |
| 刪除帳號 | 軟刪除並就地抹除身分欄位（含密碼雜湊覆寫為隨機值），發文與留言保留但作者匿名化 |

### CSP：`style-src` 已收緊為 `'self'`

改版前 `style-src` 必須放行 `'unsafe-inline'`，因為 Naive UI 以 CSS-in-JS 在執行期
建立 `<style>` 元素注入樣式，而 `<style>` 元素正是這道政策所禁止的。
換成 Tailwind v4（建置期輸出靜態 CSS）後，這個妥協得以移除：

```
default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;
font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self';
form-action 'self'; object-src 'none'
```

這道政策實際擋下的是**執行期注入的 `<style>` 元素**（CSS-in-JS 的機制，
也是以 CSS 側通道竊取資料的途徑）、HTML 中的 `style="..."` 屬性，以及外部樣式表。

它**擋不下**透過 CSSOM 寫入的樣式（`el.style.x = y`）——CSP 不攔截 CSSOM，這是規範上的既定行為。
Vue 的 `:style` 綁定走的正是 CSSOM，因此不會被擋下；專案仍以 ESLint 禁用它，
但那是為了讓樣式只有一個來源，屬於一致性而非安全考量。

真正必要的配套只有一項：**深色模式的防閃爍腳本放在 `public/theme-init.js`** 這個同源檔案，
而不是 `index.html` 裡的 inline script（那會被 `script-src 'self'` 擋下）。

可由審核者直接驗證的三項：

```bash
# 1. 應用程式帳號無法直接讀資料表 → ERROR 1142 SELECT command denied
docker compose exec db mysql -u app_user -p -e "SELECT * FROM message_me.users;"

# 2. 安全標頭：確認 style-src 已無 'unsafe-inline'
curl -sI http://localhost:3001/ | grep -i content-security-policy

# 3. 開啟 http://localhost:3001 的 DevTools Console，切換深色模式、開啟對話框、
#    上傳圖片，確認沒有任何 CSP violation
```

---

## 規格對照

| 規格要求 | 實作位置 |
| --- | --- |
| 三層式架構（Web / AP / DB） | `docker-compose.yml` 的 `web` / `app` / `db` 三個容器 |
| Vue.js 前端、Spring Boot 後端 | `frontend/`、`backend/` |
| RESTful API | 上方 API 一覽；名詞資源 + HTTP 動詞 + 標準狀態碼 |
| 會員註冊（手機、名稱、Email、密碼加鹽雜湊） | `POST /api/auth/register`、`users.password_salt` / `password_hash` |
| 登入驗證 | `POST /api/auth/login` + `JwtAuthenticationFilter` |
| 發文 CRUD（文字 + 圖片） | `PostController`、`POST /api/files/images` |
| 留言功能 | `CommentController` |
| 個人檔案（封面、自我介紹） | `PUT /api/users/me` |
| 所有 DB 存取透過 Stored Procedure | `data/repository/*` 一律 `SimpleJdbcCall`，無任何 SQL 字串 |
| 跨表異動使用 Transaction | 7 支 SP，見上方「資料庫」章節的表格 |
| DDL / DML 置於 `\DB` 資料夾 | `DB/01_DDL_schema.sql`、`02_DDL_stored_procedures.sql`、`03_DML_seed_data.sql` |
| 防範 SQL Injection 與 XSS | 見上方「安全性」 |

---

## 本機開發

需求：JDK 21、Node.js 24、可執行的 MySQL（可只啟動 `docker compose up db`）。

| 位置 | 指令 |
| --- | --- |
| `backend/` | `./mvnw spring-boot:run`（啟動於 8081）、`./mvnw verify`（全部測試） |
| `frontend/` | `npm install`、`npm run dev`（3001，`/api` 與 `/uploads` 代理至 8081） |
| `frontend/` | `npm run test`、`npm run type-check`、`npm run lint`、`npm run build` |

後端整合測試以 Testcontainers 啟動真實的 MySQL 8，載入與正式環境完全相同的 `DB/*.sql`，
因此執行 `./mvnw verify` 時需要 Docker 在背景運作。

每個 Pull Request 由 GitHub Actions 自動執行後端 `./mvnw verify` 與前端
`lint` / `test` / `type-check` / `build`（見 `.github/workflows/ci.yml`）。

### 測試涵蓋

| 層級 | 工具 | 內容 |
| --- | --- | --- |
| Stored Procedure / 資料層 | Testcontainers + 真實 MySQL 8 | 每支 SP 的正常路徑、邊界條件、Transaction 回滾 |
| 業務層 | JUnit 5 + Mockito | 業務規則、授權判斷、例外情境 |
| 展示層 | `@WebMvcTest` + MockMvc | 狀態碼、輸入驗證、未授權存取被拒絕 |
| 前端 | Vitest + Vue Test Utils | Axios 攔截器、`authStore`、導航守衛、表單驗證、主題切換、元件行為 |

目前規模：後端 **178** 個單元測試 + **94** 個整合測試，前端 **77** 個測試。

整合測試涵蓋每一支新增 SP 的正常路徑、邊界與回滾，其中特別驗證了幾個容易寫錯的地方：
按讚與取消按讚的冪等性、非本人刪除發文時附屬資料的完整回滾、
標籤在編輯後的整組替換與使用次數增減、軟刪除後手機號碼的釋放與重新註冊。

---

## 端對端驗收

1. `cp .env.example .env` 並填入 `JWT_SECRET`，執行 `docker compose up --build`
2. 開啟 `http://localhost:3001`，依序驗證**規格要求的流程**：
   註冊 → 登入 → 發文（含上傳圖片）→ 編輯發文 → 留言 → 刪除發文（確認留言一併消失）
   → 編輯個人檔案 → 登出
3. 驗證**額外功能**：
   - 發文內容輸入 `#測試`，送出後標籤成為連結，點擊可看到該標籤底下的發文
   - 對發文按讚，**連按兩次計數仍為 1**；重新整理後狀態保持
   - 以另一個帳號登入，確認對方按的讚不會顯示成自己按的
   - 點擊作者名稱進入個人檔案頁，確認發文與留言交錯出現在同一條時間軸上、新的在最上面
   - 搜尋中文關鍵字（例如「咖哩」）應有結果；搜尋單一字元（例如「山」）也應有結果
   - 切換深色模式後重新整理，偏好被記住且**沒有白底閃爍**
   - 動態牆往下捲動應自動載入更多；刪除一則發文後**捲動位置不跳動**
4. 未登入狀態直接呼叫 `POST /api/posts` 與 `POST /api/posts/1/likes`，應回 `401`
5. 以他人帳號登入後嘗試編輯不屬於自己的發文與留言，應回 `403`
6. 以 `<script>alert(1)</script>` 與 `' OR '1'='1` 作為**發文內容與搜尋關鍵字**送出，
   確認內容被清洗、原樣顯示為文字，且查詢行為不受影響；搜尋 `%` 應回 0 筆（萬用字元已跳脫）
7. 於帳號設定修改密碼（舊密碼錯誤應被拒），再以新密碼登入
8. 於帳號設定刪除帳號，確認：無法再登入、原手機號碼可重新註冊、
   **過去的發文與留言仍在**且作者顯示為「已刪除的使用者」
9. `docker compose exec db mysql -u app_user -p -e "SELECT * FROM message_me.users;"` 應被拒絕
10. 執行「資料庫」章節的計數稽核查詢，三欄應全為 `0`

---

## 文件

- [設計文件](docs/design.md) — 架構決策、資料庫設計、API 規格、安全措施與取捨說明
- [`DB/README.md`](DB/README.md) — 資料表、索引與 Stored Procedure 說明
- [需求規格](original_spec.md)
