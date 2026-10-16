# 舊版升級、備份及安全資料遷移要求

## 檔案與資料概況
- App package `tw.junba.reminder`；目前 `ReminderStore` 的 `SharedPreferences('junba')` 包含 `notes` JSON、`next_note_id`。
- 單筆記事主要鍵：`id`, `title`, `content`, `time` (epoch 毫秒), `ahead` (分鐘), `repeat` (`不重複/每日/每週/每月`), `remind`。較舊版本可能缺少 `content`、`remind`；匯入需補預設值而非拒絕或刪除。
- NativeLinkStore 的 `native_links` 是『App 曾發出原廠建立請求／核對紀錄』，不是 vivo 原廠時鐘資料庫的完整備份。
- Google Calendar 的事件應重新從手機 CalendarProvider/使用者 Google 帳號授權同步；App 只備份自己選擇的來源/鏡像元資料與排程狀態，不保證備份 Google 雲端資料。
- Gemini API Key 屬秘密，除非使用者明確同意安全轉移，不應寫入一般 JSON 備份。

## 既有程式缺陷
`MainActivity.onActivityResult(IMPORT_REQUEST)` 直接執行 `ReminderStore.save(this,notes)` 和 `NativeLinkStore.save(this,links)`，等於完全取代現有資料。請立即修正；不要宣稱『可無損續用』直到驗證。

## 建議無損遷移流程
1. 舊版仍在時，匯出到使用者可找到的位置（例如 Downloads/DocumentProvider）；同時檢查檔案非零大小、合法 JSON、筆數及 checksum。
2. Windows 工具偵測舊/新 APK 的簽章與套件；若可原地覆蓋升級則不先卸載；若不相容，先確定可還原備份且得到明確許可才卸載。
3. 新版初次啟動提供「匯入舊版備份」，**先預覽**：原有 X 筆、備份 Y 筆、擬新增 A 筆、重複 B 筆、衝突 C 筆。
4. 使用 stableId/來源識別 + legacy id + normalized content/time/repeat 做保守去重；衝突不得自動刪除已有條目；交易性保存並保留可回滾備份。
5. 合併後恢復 next_note_id，避免新建記事和舊 id 撞號；重新排程需要重新授權，逐筆回報可排程/過期/權限不足。
6. Google 日曆重新要求/確認權限與選取來源，開始同步既有/新增事件，避免鏡像重複排程。
7. 完成後讓使用者檢查筆數、日期、提醒聲音及鎖屏觸發。

## 不可避免的限制
- 簽章不同的 APK 無法無條件覆蓋；移除 App 會清除 App 私有資料。
- 舊版若沒有匯出功能且非 debuggable，第三方安裝腳本一般無法讀取私有 SharedPreferences。
- 若舊版已被解除安裝且未備份，不能承諾恢復已刪的私有記事。
- 完整直接寫入 vivo 原廠時鐘不是 Android 公開 API 的保證功能。
