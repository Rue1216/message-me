# 標籤改由輸入框指定，不再解析內文 hashtag

## 問題

發文的標籤目前由後端從內文解析：`TagExtractor` 以正規表達式抓出 `#登山`，
`PostService` 再把結果交給 `sp_post_create`。

結果是同一段文字在畫面上出現兩次——`PostCard` 先原樣印出內文（含 `#登山`），
下方又跑一排 `TagChip`（`登山`）。使用者看到的是重複的標籤文字。

原因在於「標籤」與「內文」被混為同一個欄位：`#登山` 既是句子的一部分，
又是結構化的分類資料。任何一種顯示方式都會讓另一種身分顯得多餘。

## 目標

把標籤獨立成自己的輸入欄位，與內文分離。

- 發文表單提供標籤輸入框；標籤只從這裡來
- 內文裡的 `#` 只是一個字元，不再具有任何意義
- `PostCard` 不必改動，重複顯示自然消失

## 非目標

- 標籤的即時自動完成（需要新的後端搜尋端點與 SP，屬於另一個功能）
- 為既有正式資料撰寫遷移腳本（本專案以 `docker compose down -v` 重建）
- 標籤的重新命名、合併或刪除等管理功能

---

## 資料層：不動

`sp_post_create` 與 `sp_post_update` 收的本來就是標籤名稱的 JSON 陣列
（`p_tags_json`），以 `JSON_TABLE` 展開成資料列。標籤的來源從「解析內文」
換成「使用者直接指定」，對 Stored Procedure 而言是同一件事。

**沒有任何 SQL 結構變更。**

唯一的資料異動在 `DB/03_DML_seed_data.sql`：示範發文的內文拿掉 `#` 前綴
（`去#登山` → `去登山`），`post_tags` 的關聯與 `tags.post_count` 維持原樣。
不改的話，新環境一開起來看到的第一批發文仍然是重複顯示的樣子。

---

## 後端

### `TagNormalizer` 取代 `TagExtractor`

`common/util/TagExtractor` 刪除，新增 `common/util/TagNormalizer`。

```java
List<String> normalise(List<String> raw)
```

處理順序：

1. `null` 清單視為空清單，回傳 `List.of()`
2. 逐項 `trim()`；`null` 或去除空白後為空字串者**跳過**（不視為錯誤——空項目不帶任何意圖）
3. `toLowerCase(Locale.ROOT)`。用 `ROOT` 而非系統預設，避免土耳其語地區把 `I` 轉成無點的 `ı`，
   造成同一個標籤在不同伺服器上正規化出不同結果
4. 先檢查長度是否超過 `MAX_TAG_LENGTH`，再檢查字元集 `^[\p{L}\p{N}_]+$`。
   兩者分開檢查而非合寫成一條 `{1,50}` 的正規表達式，否則「太長」與「有怪字元」
   會回同一句訊息，使用者不知道該改哪裡。任一項不符即拋
   `BusinessException(VALIDATION_ERROR)`
5. `LinkedHashSet` 去重並保留首次出現的順序，讓同一次輸入每次都得到相同結果

保留兩個常數（連同它們的理由一併從 `TagExtractor` 搬過來）：

- `MAX_TAGS_PER_POST = 10`
- `MAX_TAG_LENGTH = 50`（對齊 `tags.name` 的 `VARCHAR(50)`）

字元集限制為文字、數字與底線是刻意的，理由不變：標籤不可能含有逗號，
資料層才能安全地用 `GROUP_CONCAT` 把它們攤平成一欄；也不可能含有引號或反斜線，
JSON 序列化不會出現跳脫上的意外。這段註解必須跟著搬到新類別，
否則下一個人會以為這只是隨手訂的規則。

### 錯誤訊息

| 情形 | 訊息 |
| --- | --- |
| 含不合法字元 | `標籤只能使用文字、數字與底線` |
| 超過 50 字 | `標籤不可超過 50 字` |
| 超過 10 個 | `標籤最多 10 個` |

**數量上限對「原始清單」與「去重後的清單」同時成立**：送 11 個項目即使其中兩個重複，
仍然回 400，去重不會把超量的清單救回來。前端在送出前已經去重，
原始數量超過 10 只會來自手工構造的請求，對它誠實回報「標籤最多 10 個」即可。
這樣兩層檢查的界線一致，不會出現「DTO 擋下但業務層其實會放行」的矛盾。

### 標籤不經 `HtmlSanitizer`

`content` 仍照舊清洗，標籤則否。字元集 `[\p{L}\p{N}_]` 本身就排除了
`<`、`>`、`&` 與所有引號，比 Jsoup 的 `Safelist.none()` 更嚴格——
先驗證再入庫，清洗無事可做。

這一點要寫在 `TagNormalizer` 的類別註解裡。專案其他寫入路徑一律清洗，
這裡的例外若沒有說明，讀的人會以為漏了一道防線。

### DTO 與呼叫鏈

`PostRequest` 加一個欄位：

```java
@Size(max = 10, message = "標籤最多 10 個") List<String> tags
```

`null` 允許（等同不帶標籤），由 `TagNormalizer` 收斂成空清單。
DTO 這層只做便宜的數量粗篩，字元集與正規化交給 `TagNormalizer` ——
`List<@Pattern...String>` 的錯誤訊息指不出是第幾個標籤，
而且去重必須發生在小寫之後，Bean Validation 表達不了這個順序。

呼叫鏈跟著加一個參數：

- `PostController.create` / `update` → 多傳 `request.tags()`
- `PostService.create(long userId, String content, String image, List<String> tags)`
- `PostService.update(long postId, long userId, String content, String image, List<String> tags)`

兩者內部把 `tagExtractor.extract(safeContent)` 換成 `tagNormalizer.normalise(tags)`。

---

## 前端

### 新元件 `components/tag/TagInput.vue`

`v-model` 綁定 `string[]`。元件自己管一個未送出的輸入緩衝，不外露。

**互動**

| 操作 | 行為 |
| --- | --- |
| Enter、逗號、空白 | 把緩衝內容送出成一顆 chip |
| 失焦 | 同上，未送出的字不會默默消失 |
| 緩衝為空時按 Backspace | 刪掉最後一顆 chip |
| 點 chip 上的 × | 刪掉該顆 |
| 點熱門標籤 | 加入該標籤 |

空白之所以也是分隔符：標籤本來就不能含空白，
使用者打空白時的意圖必然是「換下一個標籤」。

送出時緩衝內容先依分隔符（逗號與空白）切開再逐一處理，
因此貼上 `登山, 露營 攝影` 會一次得到三顆 chip，而不是一段驗證失敗的文字。

**顯示正規化後的形式**。打 `Vue3` 得到的 chip 是 `vue3`——
這與實際存進資料庫的、以及 `TagChip` 在其他地方顯示的一致。
正規化立刻可見，不是送出後才悄悄發生的改寫。

**驗證回饋**

- 不合法字元、超過 50 字、已有 10 顆 → 不新增 chip，緩衝內容保留讓使用者可以修改，
  並在下方以 `role="alert"` 說明原因
- 重複（正規化後相同）→ 靜默清空緩衝，不報錯。使用者要的結果已經在畫面上了
- 任何一次成功新增或刪除都清掉錯誤訊息

**熱門標籤快選**

輸入框下方列出最多 8 個熱門標籤，點一下加入。複用現有的 `fetchPopularTags`
與 `tagKeys.popular()` query key，`staleTime` 與 `PopularTags.vue` 相同的 5 分鐘——
同一頁若側欄也開著熱門標籤，共用快取讓兩者只請求一次。
已經選過的標籤不列出；載入中或沒有資料時整區不顯示（它是輔助，不是必要資訊）。

**無障礙**

- 輸入框有可見的 `<label>`（「標籤（選填）」），id 以 `useId()` 產生，
  理由同 `PopularTags.vue`：同一頁可能有多個實例
- chip 清單為 `<ul>`，每顆的 × 有 `aria-label`（如「移除標籤 登山」）
- 錯誤訊息以 `aria-describedby` 連到輸入框

### 新檔 `utils/validation/tag.ts`

與 `validation/post.ts` 同一套原則：上限值對齊後端，前端驗證只為及早回饋，
不是安全邊界。

匯出 `TAG_MAX_LENGTH`、`MAX_TAGS_PER_POST`、`normaliseTag(value)`
與 `validateTag(value, existing)`。`validateTag` 回傳錯誤訊息或 `null`，
訊息文字與後端一致。

### `PostForm.vue`

- 新增 `initialTags?: string[]` prop（預設 `[]`）
- 內部 `tags` ref，模板中放一個 `<TagInput v-model="tags" />`，位置在內文與圖片之間
- `handleSubmit` 送出的 payload 帶 `tags`
- `reset()` 一併清空 `tags`
- placeholder 預設值改成 `分享你的想法…`，不再提示 `#標籤`
- 標籤為選填，空清單不阻擋送出

**草稿格式改為 JSON**：`sessionStorage` 存 `{ "content": string, "tags": string[] }`。
讀取時先 `JSON.parse`，只有在結果是「帶字串 `content` 的物件」時才採用；
其餘情形（parse 失敗、或 parse 出字串）一律把原始字串當成內文、標籤為空。
這讓改格式前留在分頁裡的純文字草稿不會消失。

寫入條件維持原樣的精神：內文有字**或**有標籤才存，兩者皆空則移除。

### 其他改動

| 檔案 | 改動 |
| --- | --- |
| `types/api.ts` | `PostPayload` 加 `tags: string[]` |
| `components/post/PostEditorModal.vue` | `<PostForm>` 傳 `:initial-tags="post.tags"` |
| `components/tag/PopularTags.vue` | 空狀態文案 → `還沒有人使用標籤。發文時加上標籤就會出現在這裡。` |

`PostCard.vue`、`api/resources/posts.ts`、`composables/usePostMutations.ts`
與 `views/HomeView.vue` **不需要改動**：payload 是整包傳遞的，
型別加欄位就自動流過去；重複顯示的消失是因為內文不再含 `#`。

---

## 文件

| 檔案 | 改動 |
| --- | --- |
| `README.md:13` | 「`#標籤` 自動解析」→「標籤（發文時以輸入框指定）」 |
| `docs/design.md:31` | 同上，說明改為「發文時由標籤輸入框指定」 |

`docs/design.md` 其餘提到標籤的段落（SP 清單、交易邊界、`JSON_TABLE`）
描述的都是資料層，不受影響。

---

## 測試

### 後端

`TagExtractorTest` 改寫為 `TagNormalizerTest`：

- 大小寫正規化（`Vue3` → `vue3`）
- 去重並保留首次出現的順序
- 前後空白去除
- 空字串與 `null` 項目被跳過
- `null` 清單回傳空清單
- 中日韓字元通過（`登山`）
- 不合法字元被拒（`台北101!`、含空白、含逗號）
- 超過 50 字被拒
- 超過 10 個被拒，且「11 個含重複」同樣被拒

`PostServiceTest`、`PostControllerTest`、`PostApiIT`：呼叫端補上 tags 參數，
並新增一則「內文寫 `#登山` 但標籤清單為空 → 該發文沒有任何標籤」的案例，
把「不再解析內文」這件事釘在測試裡。

### 前端

新增 `components/tag/__tests__/TagInput.spec.ts`：

- Enter、逗號、空白三種鍵都能 commit
- 一次送出多個（`登山, 露營 攝影` → 三顆 chip）
- 失焦時 commit 未送出的字
- 緩衝為空時 Backspace 刪掉最後一顆；緩衝有字時不刪
- × 按鈕刪除指定的一顆
- 不合法字元顯示錯誤且不新增，緩衝內容保留
- 重複標籤靜默忽略，不顯示錯誤
- 第 11 顆被擋下並顯示訊息
- 點熱門標籤加入，且該標籤自建議清單消失

新增 `components/post/__tests__/PostForm.spec.ts`（目前沒有 PostForm 的測試）：
草稿新格式的讀寫、舊純文字草稿的相容路徑、標籤隨 payload 送出、`reset()` 一併清空標籤。

---

## 實作順序

1. 後端 `TagNormalizer` 與其測試（純函式，不依賴其他改動）
2. 後端 DTO、Service、Controller 串接，更新既有測試
3. `DB/03_DML_seed_data.sql` 內文去 `#`
4. 前端 `utils/validation/tag.ts`
5. 前端 `TagInput.vue` 與其測試
6. `PostForm.vue`、`PostEditorModal.vue`、`types/api.ts` 串接
7. `PopularTags.vue` 文案、`README.md`、`docs/design.md`
