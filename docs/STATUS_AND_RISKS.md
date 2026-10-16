> 歷史 R6 設計：使用者已改採 R7 簡化流程，請先閱讀 [R7_SIMPLE_WORKFLOW.md](R7_SIMPLE_WORKFLOW.md) 與專案 README；不要恢復 App 內 AI／日曆瀏覽。

# 2026-10-16 專案現況與風險（原始碼盤點；非真機驗收）

## 已看到的程式
- `app/build.gradle`：versionCode 6 / versionName `0.1.6-R6`，Gradle Android 插件版本在根 `build.gradle`。
- `MainActivity.java`：記事主題/內容輸入；空白回填『無訊息』；語音RecognizerIntent及 Gemini 解析後確認流程；手動 JSON 匯出/匯入。
- `GeminiNoteParser.java`：Gemini `generateContent` 並解析 JSON；需要 API 可用性、逾時與安全輸出驗證。
- `ReminderStore.java`：SharedPreferences `junba` 的 `notes` JSON，使用 `AlarmManager.setAlarmClock()` 對有提醒的記事建立 App 自有 AlarmReceiver。
- `CalendarMirrorStore.java`：系統日曆同步到本機精確提醒（仍需實測、多提醒/取消/時區驗收）。
- `AlarmReceiver.java` + `AlarmAudioService.java`：收到提醒時啟動前景服務，嘗試循環播放預設鬧鐘鈴聲，提供停止/貪睡。
- `BootReceiver.java`：開機/套件替換後重排程。
- `.github/workflows/build-apk.yml`：有 Actions `assembleDebug` 工作流，本交接包將 artifact 名稱由 R5 修為 R6，**尚未跑 GitHub Actions**。

## 重大風險／未實作
1. 本輪已在雲端以 Java 17、Gradle 8.10.2、SDK 35 完成 `assembleDebug`，產物為 `app/build/outputs/apk/debug/app-debug.apk`，版本 `0.1.6-R6` / versionCode 6，APK 簽章驗證通過。尚未執行 GitHub Actions 或 vivo 真機測試，也不是正式 release APK。舊 `/mnt/data/app-debug.apk` 仍不可當作本輪產物。
2. JSON 匯入直接覆蓋 `notes` 和 `native_links`，不做合併／衝突提示，與使用者資料保護要求不符。
3. 匯出 JSON 目前只有 `version=4`, `notes`, `native_links`；並未完整涵蓋日曆選取、同步鏡像快照、其它設定。雲端 Google 日曆活動應在重新授權後由 CalendarProvider 再同步。
4. `android:allowBackup=false`：不可假設 Android 系統自動備份會替 App 保留資料；需在舊版存活時由 App 導出 JSON 或對允許 `run-as` 的 debug 版使用備援方法。
5. 已確認使用者提供的 R5 與 R6 debug APK 憑證不同，無法直接覆蓋。已準備固定 release signing workflow 與失敗保護測試，但正式金鑰／GitHub Secrets 尚未設定、未執行遠端流程；現有 R5 仍需原金鑰或經驗證的資料移轉。見 `APK_SIGNING.md`。
6. 原廠 Clock `AlarmClock.ACTION_SET_ALARM` 不等同完全可控的 vivo Clock 儲存資料庫，尤其自訂日期/提醒策略，原廠通知與 App 自有響鈴必須區分。
7. Android 14–16 前景服務、背景啟動、通知、鬧鐘及 vivo 背景省電策略需要真機驗證。
8. 目前輸出的備份檔預設名稱仍包含 `R5`，請 Codex 更名並同步提升備份 schema 版本。
9. `GeminiNoteParser` 解析語音前會送文字到雲端服務；須明確確認/揭露用途，並測試網路/API 失敗時草稿保留。
10. 使用者最近兩份 Windows 執行紀錄均因舊版 PowerShell `$HOME` 衝突而停止，不能從那些紀錄推斷 R5 已成功安裝。

## 資訊保證程度
『已看到程式碼』不是『已完成編譯或實測』；後續請以 GitHub Actions 成功 log 及裝置測試報告為準。

## 本輪原廠建立流程改善（雲端驗證）

- 新增手動每日／每週選項、預計下一次時間與規則預覽、已有送交紀錄的重複建立提示。
- 攔阻過期／當前分鐘、無法表達的未來開始日期與每月規則；送出按鈕會再次檢查時間。
- 23 項獨立 Java 時間規則檢查通過；`assembleDebug lintDebug` 成功，Lint 為 0 錯誤、12 警告（先前的 3 個 WrongConstant 已修正）。後續已加入 AI JUnit 測試，結果見下方「AI 流程改善」。
- 首次原廠流程改善驗證產物的 APK SHA-256（非後續 AI 改進產物）：`9b0084e669b6b51fa63c634c56eeee521dfcd3e3710c0865dfb263da87645c00`。
- 未連接裝置、未安裝／解除安裝 App、未匯入使用者資料；原廠接受設定、實際響鈴、貪睡與鎖屏仍待驗證。詳見 `NATIVE_ALARM_VALIDATION.md`。

## AI 流程改善（待合併，未發布）

- 語音主動授權後自動整理，文字送出後自動整理；自動查詢／選用可用模型，模型 404／410 時最多替換一次。
- AI 活動時間、提前分鐘與重複規則完整保留，預覽後一次確認建立，不強制再次進手動編輯器。
- 原廠目標使用 `EXTRA_SKIP_UI=true` 請求自動建立；不保證 vivo 接受或免確認，不會自動標記成功。無法表達的日期／每月規則改選 Junba，本機提醒與原廠正常流程只選一種。
- 逾時不再宣稱 Key 有誤；保留原文與解析預覽並可重試。新增 AI 草稿識別與記事原子寫入，防止重複確認新增記事。
- 38 項 AI JUnit 測試已通過；使用模擬 API 與合成資料，沒有實際 Key／語音／vivo 響鈴驗證。雲端建置不能證明使用者手機的網路逾時已解決。
- 詳見 `AI_WORKFLOW.md`。尚未合併到主分支、未執行新 Actions、未發布或安裝到手機。
