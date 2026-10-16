# 第一則交給 Codex 的工作指令（可直接複製）

請接手這個 GitHub Android 專案「Junba 智慧提醒 R6」。先完整閱讀 repo 根目錄 `AGENTS.md`、`README.md`、`docs/CODEX_TASKS.md`、`docs/DATA_MIGRATION.md`、`docs/STATUS_AND_RISKS.md`。不要重寫成空專案；沿用套件 `tw.junba.reminder`、繁體中文介面及現有 Java/Gradle 架構。

**第一階段工作：**
1. 檢查所有 Android 原始碼，執行 Gradle `assembleDebug`，修正編譯失敗，並提供具體 log 與 APK artifact；未成功不可聲稱可用。
2. 優先修復舊版備份/匯入覆蓋資料問題，改為安全合併、去重、衝突預覽及失敗回滾，維持 R3/R4/R5 JSON 相容。絕對不能為了安裝而先清除舊版資料。
3. 規劃固定 APK signing（keystore 密碼使用 GitHub Secrets，勿提交秘密），保證後續版本能在相同簽章下覆蓋更新；既有 debug 簽章不相同時提供安全轉移。
4. 確認記事標題/內容留空都寫入『無訊息』，Gemini 語音轉記事／鬧鐘的確認流程及失敗草稿保存。
5. 使 Google 日曆已存在和新增的提醒能排成 Junba 本機可響鈴鬧鐘，測試多提醒、提前 1 分鐘/30 分鐘/5 天、日曆異動/取消、重開機、權限拒絕。不要宣稱可以無限制寫入 vivo 原廠時鐘。

最後請列出：修改檔案、已完成、仍未完成、建置結果、APK 位置、真機待測項目，以及任何可能造成舊資料遺失的操作。
