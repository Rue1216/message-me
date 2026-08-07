# Message Me — 簡易社群媒體平台

以三層式架構實作的社群媒體平台，提供註冊、登入驗證、發文 CRUD 與留言功能。
所有資料庫存取一律透過 Stored Procedure，跨表異動以 Transaction 保護。

本專案為技術評測作業，需求規格見 [`original_spec.md`](original_spec.md)，
架構決策與取捨的完整說明見 [設計文件](docs/design.md)。

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
│ (Application Server 層) │  JWT 驗證、Bean Validation、@Transactional
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
| 前端 | Vue 3 + Vite + TypeScript + Pinia + Vue Router + Naive UI + Axios |
| 建置 | Maven Wrapper（後端）、npm（前端） |
| 測試 | JUnit 5 / Mockito / MockMvc / Testcontainers、Vitest / Vue Test Utils |

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

---

## API

所有端點位於 `/api` 之下。🔒 表示需要 `Authorization: Bearer <accessToken>`。

| 方法 | 路徑 | 驗證 | 說明 |
| --- | --- | :---: | --- |
| `POST` | `/api/auth/register` | | 註冊，回 `201` |
| `POST` | `/api/auth/login` | | 登入，回傳 JWT 與個人檔案 |
| `GET` | `/api/users/me` | 🔒 | 取得本人完整資料（含手機、Email） |
| `PUT` | `/api/users/me` | 🔒 | 更新個人檔案（全欄位取代） |
| `GET` | `/api/users/{userId}` | | 他人的公開檔案（不含手機、Email） |
| `POST` | `/api/posts` | 🔒 | 新增發文，回 `201` |
| `GET` | `/api/posts?page=&size=` | | 動態牆分頁，新到舊 |
| `GET` | `/api/posts/{postId}` | | 單篇發文 |
| `PUT` | `/api/posts/{postId}` | 🔒 | 編輯發文（僅本人） |
| `DELETE` | `/api/posts/{postId}` | 🔒 | 刪除發文（僅本人，連帶刪除留言） |
| `POST` | `/api/posts/{postId}/comments` | 🔒 | 新增留言，回 `201` |
| `GET` | `/api/posts/{postId}/comments?page=&size=` | | 留言分頁，舊到新 |
| `DELETE` | `/api/comments/{commentId}` | 🔒 | 刪除留言（僅本人） |
| `POST` | `/api/files/images` | 🔒 | 上傳圖片（`multipart/form-data`，欄位名 `file`） |
| `GET` | `/api/health` | | 健康檢查 |

分頁參數 `page` 自 1 起算，`size` 上限 100。

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
（前端據以分支），`error.message` 僅供顯示。分頁資料再包一層
`{ items, page, size, totalElements, totalPages }`。

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

三張資料表 `users` / `posts` / `comments`，14 支 Stored Procedure，欄位與索引說明見
[`DB/README.md`](DB/README.md)。應用程式**沒有**資料表的 CRUD 權限，只被授予 SP 的 `EXECUTE`。

跨表異動由 SP 內的 Transaction 保護，並搭配 `DECLARE EXIT HANDLER FOR SQLEXCEPTION`
做 `ROLLBACK` 與 `RESIGNAL`：

| Stored Procedure | 跨表異動 |
| --- | --- |
| `sp_post_delete` | 刪除發文的全部留言 + 刪除發文 |
| `sp_comment_create` | 新增留言 + 遞增 `posts.comment_count` |
| `sp_comment_delete` | 刪除留言 + 遞減 `posts.comment_count` |

---

## 安全性

| 面向 | 措施 |
| --- | --- |
| SQL Injection | 全面使用 Stored Procedure、`CallableStatement` 參數綁定、SP 內無動態 SQL、應用程式帳號僅有 `EXECUTE` 權限、連線字串 `allowMultiQueries=false` |
| XSS | 輸入端以 Jsoup `Safelist.none()` 清洗、輸出端一律 `{{ }}` 自動轉義並以 ESLint `vue/no-v-html` 封鎖 `v-html`、Nginx 注入 CSP 等安全標頭 |
| 密碼 | PBKDF2-HMAC-SHA256（310,000 輪）+ 每人獨立 32-byte 隨機鹽，存於 `password_salt` 欄位；比對使用常數時間方法 |
| 身分驗證 | 無狀態 JWT（HS256），密鑰由環境變數注入；預設拒絕，僅白名單端點公開 |
| 授權 | 業務層與 SP 雙重比對 `user_id`，單層失效仍擋得住越權 |
| 檔案上傳 | magic number 嗅探（不信任 MIME 與副檔名）、5MB 上限、UUID 重新命名、寫入前驗證落點、Nginx 以唯讀且 `sandbox` 政策提供 |
| 權杖保存 | 前端存於 `sessionStorage` 而非 `localStorage`，分頁關閉即失效 |

可由審核者直接驗證的兩項：

```bash
# 應用程式帳號無法直接讀資料表 → ERROR 1142 SELECT command denied
docker compose exec db mysql -u app_user -p -e "SELECT * FROM users;"

# 安全標頭
curl -I http://localhost:3001/
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
| 跨表異動使用 Transaction | `sp_post_delete` / `sp_comment_create` / `sp_comment_delete` |
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
| 前端 | Vitest + Vue Test Utils | Axios 攔截器、`authStore`、導航守衛、表單與元件行為 |

---

## 端對端驗收

1. `cp .env.example .env` 並填入 `JWT_SECRET`，執行 `docker compose up --build`
2. 開啟 `http://localhost:3001`，依序驗證：
   註冊 → 登入 → 發文（含上傳圖片）→ 編輯發文 → 留言 → 刪除發文（確認留言一併消失）
   → 編輯個人檔案 → 登出
3. 未登入狀態直接呼叫 `POST /api/posts`，應回 `401`
4. 以他人帳號登入後嘗試編輯不屬於自己的發文，應回 `403`
5. 以 `<script>alert(1)</script>` 與 `' OR '1'='1` 作為發文內容送出，
   確認內容被清洗、原樣顯示為文字，且查詢行為不受影響
6. `docker compose exec db mysql -u app_user -p -e "SELECT * FROM users;"` 應被拒絕

---

## 文件

- [設計文件](docs/design.md) — 架構決策、資料庫設計、API 規格、安全措施與取捨說明
- [`DB/README.md`](DB/README.md) — 資料表、索引與 Stored Procedure 說明
- [需求規格](original_spec.md)
