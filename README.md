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

開發中。啟動步驟與 API 速查表將於功能開發完成後補齊於本文件；架構決策與安全性設計請見設計文件。

## 文件

- [設計文件](docs/design.md) — 架構決策、資料庫設計、API 規格、安全措施
- [需求規格](original_spec.md)
