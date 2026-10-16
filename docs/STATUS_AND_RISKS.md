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
1. 尚無可核對的 R6 APK，也未在本機完成 Android SDK Gradle 編譯或 vivo 真機測試。原 `/mnt/data/app-debug.apk` 是先前舊檔，**不能當作 R6 發佈**。
2. JSON 匯入直接覆蓋 `notes` 和 `native_links`，不做合併／衝突提示，與使用者資料保護要求不符。
3. 匯出 JSON 目前只有 `version=4`, `notes`, `native_links`；並未完整涵蓋日曆選取、同步鏡像快照、其它設定。雲端 Google 日曆活動應在重新授權後由 CalendarProvider 再同步。
4. `android:allowBackup=false`：不可假設 Android 系統自動備份會替 App 保留資料；需在舊版存活時由 App 導出 JSON 或對允許 `run-as` 的 debug 版使用備援方法。
5. 目前沒有固定 release signing，GitHub debug key 不同可能導致 INSTALL_FAILED_UPDATE_INCOMPATIBLE；不能藉由『清除認證』繞過簽章。
6. 原廠 Clock `AlarmClock.ACTION_SET_ALARM` 不等同完全可控的 vivo Clock 儲存資料庫，尤其自訂日期/提醒策略，原廠通知與 App 自有響鈴必須區分。
7. Android 14–16 前景服務、背景啟動、通知、鬧鐘及 vivo 背景省電策略需要真機驗證。
8. 目前輸出的備份檔預設名稱仍包含 `R5`，請 Codex 更名並同步提升備份 schema 版本。
9. `GeminiNoteParser` 解析語音前會送文字到雲端服務；須明確確認/揭露用途，並測試網路/API 失敗時草稿保留。
10. 使用者最近兩份 Windows 執行紀錄均因舊版 PowerShell `$HOME` 衝突而停止，不能從那些紀錄推斷 R5 已成功安裝。

## 資訊保證程度
『已看到程式碼』不是『已完成編譯或實測』；後續請以 GitHub Actions 成功 log 及裝置測試報告為準。
