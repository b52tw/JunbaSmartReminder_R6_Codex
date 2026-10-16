# Junba 智慧提醒 R6 — Codex 專案交接包

這是一份**現有 Android 原始碼**及交接文件，可供 Codex 在 GitHub repository 中接續開發；**不是已成功編譯或真機驗證的 APK**。

- 應用套件：`tw.junba.reminder`（**不可任意變更**，否則會失去既有升級路徑）
- 當前程式設定：`versionCode 6`、`versionName 0.1.6-R6`
- Android：`minSdk 26`、`targetSdk/compileSdk 35`；主要驗證裝置 vivo V2413 / Android 16
- 語言：介面及文件使用繁體中文；使用者是 Windows 安裝、Android 手機測試情境
- 原始碼：`app/src/main/java/tw/junba/reminder/`
- GitHub Actions：`.github/workflows/build-apk.yml`（現為 debug APK 流程，仍需跑 CI 驗證）

## 立即開始

1. **Codex 請先閱讀 [`AGENTS.md`](AGENTS.md) 與 [`docs/CODEX_TASKS.md`](docs/CODEX_TASKS.md)**。
2. 將此資料夾的內容放在 GitHub repository **根目錄**，不要多包一層資料夾。
3. GitHub Actions 手動執行 `Build Android APK`，檢視 `assembleDebug` 是否成功。
4. 建置成功可下載 artifact `JunbaSmartReminder-v0.1-R6-debug-APK`；**debug 簽章不保證能直接覆蓋已安裝 R4/R5**，不可為了解決安裝衝突而未備份就卸載。
5. Codex 最先處理備份/安全移轉及可重複簽章，才進入功能與 UI 改善。

## 已有的程式（請視為待驗證）

- 記事標題與內容欄位；空白值使用「無訊息」；記事儲存、編修、重複提醒。
- Android 語音辨識將口述轉文字，再由 Gemini API 嘗試解析記事/提醒資訊，**確認後才送出**。此流程依賴裝置可用的 Android 語音辨識服務，不保證完全離線。
- 日曆讀取與部分活動提醒的本機 AlarmManager 鏡像；不是保證寫入 vivo 原廠時鐘。
- Android 前景服務播放系統預設鬧鐘鈴聲，支援停止／5 分鐘稍後提醒；仍需測試系統通知/背景/勿擾限制。
- 既有 JSON 匯出/匯入功能，但**現有匯入會直接覆蓋本機記事與連動紀錄**，還不是安全的合併移轉。

## 尚未完成、不可宣稱已完成

- R6 APK 的正式編譯、vivo 真機測試、鎖定畫面響鈴測試。
- 固定簽署金鑰、跨不同 debug 簽章的無損升級流程。
- 所有舊版記事/日曆提醒的自動合併、去重及回復。
- 把所有 Google 日曆提醒自動存到 vivo 原廠時鐘（Android 公開 API 不保證支援）。
- 任意長期的背景日曆同步、所有重複規則與多提醒設定的全面相容。

## 專案隨附

- [`AGENTS.md`](AGENTS.md)：Codex 必須遵守的安全／規格約束
- [`docs/CODEX_TASKS.md`](docs/CODEX_TASKS.md)：優先順序與驗收標準
- [`docs/STATUS_AND_RISKS.md`](docs/STATUS_AND_RISKS.md)：已完成／未完成及已知風險
- [`docs/DATA_MIGRATION.md`](docs/DATA_MIGRATION.md)：資料與升級安全設計
- [`docs/START_CODEX_PROMPT.md`](docs/START_CODEX_PROMPT.md)：可直接貼給 Codex 的第一則指令
- `reference/previous_sources/`：先前 R4/R5 候選原始碼存檔，供比較資料格式（不是目前的建置輸入）

**隱私提醒：**此包不應包含真實 Gemini API Key、手機記事備份、簽章 keystore 或使用者的私人日曆資料。

## APK 簽章與更新

R5／R6 的 Actions debug APK 可能使用不同金鑰，無法直接覆蓋安裝。已新增手動 `Build Signed Android APK` 工作流程，需先設定固定金鑰的 GitHub Secrets 與預期憑證指紋；未設定會停止，不會生成臨時金鑰替代。這無法自動解決既有 R5 的簽章不相容，請先保留舊版與資料。設定與安全移轉限制見 [`docs/APK_SIGNING.md`](docs/APK_SIGNING.md)。

## AI 語音與一句話建立（工作區改進）

已改為主動按語音交給 AI／輸入一句話 → 自動選可用模型 → 一次預覽確認建立記事與所選鬧鐘。提前分鐘與重複規則會保留；逾時可直接重試，草稿不清除。原廠以公開 API 請求自動建立，仍受時鐘支援與日期限制。這些改動尚未合併到主分支或真機驗證，詳見 [`docs/AI_WORKFLOW.md`](docs/AI_WORKFLOW.md)。
