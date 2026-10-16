# Junba 簡易鬧鐘 R7

依使用者最新決定，移除 App 內 Gemini／API Key、語音 AI 解析及日曆瀏覽，改為簡單記事交給原廠時鐘核對。作者：峻爸。

- 套件：`tw.junba.reminder`（保持原套件與資料位置）
- 版本：`versionCode 7`、`versionName 0.2.0-R7-simple`
- Java 17、Gradle 8.10.2、Android SDK 35；minSdk 26、targetSdk 35
- 主要真機驗證目標：vivo V2413 / Android 16

## 記事鬧鐘

1. 輸入鬧鐘名稱、選填內容、選年月日與時間。
2. 按「儲存並開啟原廠」，先保存記事，再用 Android `ACTION_SET_ALARM` 帶入名稱、內容、時、分，要求顯示時鐘介面。
3. **在原廠核對日期、時間並完成儲存。** Android 公開介面沒有完整年月日欄位；名稱裡附的日期只是核對文字，不會自動設定原廠日期。手機時鐘的實際畫面仍需真機確認。
4. 可編輯或刪除記事；修改／刪除記事不會修改／刪除已送出的原廠鬧鐘。按「開原廠核對／刪除鬧鐘」到原廠處理。

新記事不另建 Junba 響鈴排程。舊版記事、原廠請求紀錄及仍啟用的舊記事排程保留；編輯儲存該筆時才取消其舊 Junba 排程。草稿自動保存，編輯草稿繼續對應原記事；重複按儲存不重複新增。原廠請求只能記為「已送出，待核對」，不能假定建立成功。

## Google 日曆通知響鈴

預設關閉。在「日曆通知響鈴／設定」主動啟用並授予通知存取、精確鬧鐘及 Junba 通知權限後，只轉接 `com.google.android.calendar` 實際到達的新提醒通知。由 Junba 透過 AlarmManager 和前景服務播放系統預設鬧鐘鈴聲，**不是在原廠時鐘新增項目**。沒有讀取日曆清單、活動瀏覽或補排活動功能，也沒有網路權限。

Google 日曆原通知必須開啟；原日曆提示可能一起響。通知摘要、持續通知與非提醒分類會忽略；相同通知、短時間更新及來源指定只提示一次的更新會去重。授權時不重播舊通知。通知未送達、OEM 背景限制、勿擾或靜音可能影響響鈴，仍須真機驗證。系統的通知存取授權較廣，本程式在讀取文字前先排除其他 App。

## 建置與下載

```sh
gradle --no-daemon --max-workers=2 assembleDebug assembleRelease testDebugUnitTest lintDebug
python3 tests/test_release_signing.py app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/apk/debug/app-debug.apk -v
```

GitHub Actions：`Build Android APK (debug development only)`。選擇這次修改所在分支執行，成功後下載 `JunbaSmartReminder-R7-simple-debug-APK`，解壓後安裝 `app-debug.apk`。ZIP 另附 APK 摘要、簽章憑證與來源 commit。

**不同 Actions 執行的 debug 簽章不保證相同，不能保證直接覆蓋手機舊版。不要未備份就解除安裝。** 固定 release 簽章流程仍需由儲存庫管理者設定 Secrets 與憑證指紋，詳見 [APK_SIGNING.md](docs/APK_SIGNING.md)；未設定不會產生正式簽章 APK。

## 驗證與資料

自動檢查涵蓋日期／時區、空白欄位、既有資料保留、編輯與儲存去重、通知來源與更新過濾、簽章流程。沒有在雲端執行 vivo 真機的日期核對、鎖屏及通知響鈴，請依 [R7 驗證清單](docs/R7_SIMPLE_WORKFLOW.md) 測試。

備份可匯出記事及原廠請求紀錄，寫入後重新讀取比對；不含原廠時鐘資料、舊 API Key，也沒有直接覆蓋資料的匯入功能。舊加密 Key 保留在原 App 私有儲存區但不再使用。舊本機日曆鏡像排程停止，來源日曆活動不改動。

開發先閱讀 [AGENTS.md](AGENTS.md)。其他 R6／AI 交接文件屬歷史設計，R7 以本文件與使用者最新指示為準。私人記事、日曆資料、API Key、keystore 不得提交至 GitHub。
