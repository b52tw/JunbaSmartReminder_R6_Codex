# Junba 簡易鬧鐘 R10

作者：峻爸。記事、Google 日曆提醒可納入 Junba 的 Android 系統鬧鈴，從 App 查看、測試、延後及移除連動。保留月份與帳號選擇；所有 Google 原活動與通知維持唯讀。沒有 AI、API Key 或網路權限。

- package：`tw.junba.reminder`；versionCode 10、versionName `0.5.0-R10-links`
- Java 17、Gradle 8.10.2、SDK 35、minSdk 26；主要真機目標 vivo V2413／Android 16

## 燈號與清單

- 🔴 **已連動**：正式 Junba 系統排程曾成功提交。未來提醒需具備通知與精確鬧鐘權限；已執行的連動仍顯示處理狀態，移除後熄燈。
- 🟠 **正在響鈴**：清楚標示當前記事／日曆／測試來源，App 橘色底、通知也加色彩及文字。
- ○ **未連動／已移除**：排程失敗、缺權限、未提供時間或自行移除，不假亮。
- 🔵 **測試**：3 秒後經相同系統排程及音效流程響 5 秒，不建立正式連結、不改正式紅燈。

首頁「所有系統鬧鈴連動／測試」可看記事、日曆預排及實際通知的連動時間與狀態，每筆可測試、延後 10／30／60 分鐘及移除。上方固定停止只停止 Junba 聲音，Google 原提示與原廠鬧鐘各自處理。

## 日曆預先連動與刷新

主動允許讀取手機日曆、Junba 通知、精確鬧鐘並啟用日曆連動後，讀取所選帳號／日曆的明確提醒時間，預排未來 90 天活動的通知，本次最多 250 筆。月份紅燈表示已納入鬧鈴，不必等收到通知。每筆活動仍顯示帳號、日曆及原提醒資料。

可按「刷新 Google 日曆與鬧鈴連動」；開 App／重新進入會刷新，背景嘗試監聽手機 CalendarProvider 變更並每 6 小時更新。讀取的是手機已同步資料，Google 雲端同步及省電／強制停止限制仍由手機控制。過期不突然補響；超出範圍可從活動自行指定 Junba 時間。

全天生日、Google 工作或預設提醒若沒有提供資料／確切時間，不猜測、不假亮。可自行指定 Junba 時間，Google 原通知不變。已預排的同名近時 Google 通知盡量沿用原鬧鈴，實際收到的通知仍有獨立紀錄。相同名稱／帳號歧義與省電背景限制需真機驗證。

## 記事與原廠

填名稱、內容、完整年月日時間，明確選「儲存並連動 Junba」或「儲存並開原廠」。記事先保存，排程失敗仍保留；空白值以「無訊息」保存。原有記事可單獨新增／移除 Junba 連動，舊版本機排程成功轉入清單後才退役，重複規則繼續依原資料計算。

原廠只透過公開 `ACTION_SET_ALARM` 接收名稱、文字與時分，完整日期仍由本人核對並儲存。App 無法讀回、確認或直接取消原廠鬧鐘；原廠已建項目與 Junba 連動獨立，須到原廠另外取消。

## 保留來源與可收折設定

移除連動只取消 Junba 的 AlarmManager 項目，記事／Google 原活動／生日／工作與原通知不改、不刪。自行移除的連動會持久記住，刷新不自行復活；重新連動須由本人操作。從月份整筆移除會記住該次活動的選擇。App 不提供 Google 新增／修改／刪除入口，也不取消或延後 Google 原通知。

設定頁的授權、日曆連動、測試工具、資料與版本均可展開／收折；授權完成返回時可自動收折。備份 version 7 包含記事、App 連動紀錄、月份選擇及移除選擇；不含 Google 原活動或原廠時鐘，也未提供完整自動還原，不能據此直接解除安裝。

## 建置、下載與驗證

```sh
python3 tests/test_calendar_readonly.py -v
gradle --no-daemon --max-workers=2 assembleDebug assembleRelease testDebugUnitTest lintDebug
python3 tests/test_release_signing.py app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/apk/debug/app-debug.apk -v
```

GitHub Actions 成功後下載 `JunbaSmartReminder-R10-links-debug-APK`，解壓安裝 `app-debug.apk`。debug 簽章不保證覆蓋舊版，遇衝突先保留舊版與資料；固定正式簽章仍需 GitHub Secrets，見 [APK_SIGNING.md](docs/APK_SIGNING.md)。

本機 debug／release、77 項 JUnit、3 項 Google 唯讀合約、9 項簽章工具回歸通過；Lint 0 錯誤／5 警告。尚未在雲端執行 vivo CalendarProvider、鎖屏／背景、實際通知與原廠畫面測試。詳見 [R10 流程與驗收](docs/R10_LINKS.md)，開發遵守 [AGENTS.md](AGENTS.md)。R6–R9 文件保留為歷史設計，以 R10 及使用者最新決定為準。
