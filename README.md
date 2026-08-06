# Message Me — 簡易社群媒體平台

以三層式架構實作的社群媒體平台，提供註冊、登入驗證、發文與留言功能。

本專案為技術評測作業，需求規格見 [`original_spec.md`](original_spec.md)。

## 技術架構

| 層級 | 技術 |
| --- | --- |
| Web Server | Nginx（靜態檔服務 + API 反向代理 + 安全標頭） |
| Application Server | Spring Boot 3.4 / Java 21 / Spring Security 6 + JWT |
| 資料層 | MySQL 8，所有存取一律透過 Stored Procedure |
| 前端 | Vue 3 + Vite + TypeScript + Pinia + Naive UI |
| 建置 | Maven（後端）、npm（前端） |
| 執行環境 | Docker Compose |

## 專案狀態

開發中。API 速查表與規格對照表將於功能開發完成後補齊於本文件；架構決策與安全性設計請見設計文件。

## 啟動方式

```bash
cp .env.example .env     # 填入 JWT_SECRET 與資料庫密碼
docker compose up --build
# 開啟 http://localhost:8080
```

三個容器分別對應三層式架構：`web`（Nginx，唯一對外的 8080 埠）、`app`（Spring Boot，8081）、`db`（MySQL 8.4，僅容器網路內可存取）。若本機 8080 埠已被占用，於 `.env` 調整 `WEB_PORT` 即可。

## 本機開發

| 位置 | 指令 |
| --- | --- |
| `backend/` | `./mvnw spring-boot:run`（啟動於 8081）、`./mvnw verify`（全部測試） |
| `frontend/` | `npm install`、`npm run dev`、`npm run test`、`npm run type-check`、`npm run lint`、`npm run build` |

後端需 JDK 21（Maven 由 `mvnw` 自動下載），前端需 Node.js 24。每個 Pull Request 皆由 GitHub Actions 自動執行上述後端與前端的完整檢查。

## 文件

- [設計文件](docs/design.md) — 架構決策、資料庫設計、API 規格、安全措施
- [需求規格](original_spec.md)
