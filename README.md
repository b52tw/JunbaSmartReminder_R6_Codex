# Junba 簡易鬧鐘 R11

作者：峻爸。記事與 Google 日曆提醒可自動納入 **Junba 鬧鈴**；要在 **vivo 原廠時鐘清單**看見鬧鐘，需逐筆送交並在原廠核對日期儲存。兩種狀態分開顯示，Google 原活動與通知一律唯讀。不含 App 內 AI、API Key 或網路權限。

- package `tw.junba.reminder`；versionCode 11、versionName `0.6.0-R11-clock`
- Java 17、Gradle 8.10.2、SDK 35、minSdk 26；真機目標 vivo V2413／Android 16

## 原廠送交與本人核對

首頁「原廠鬧鐘：逐筆送交／核對紀錄」彙整未來連動及原廠單獨送交，可依序帶入每筆名稱與時分，開原廠核對、儲存，再回 App 標記「本人已核對」。多個日曆提醒可分別選時間；送交優先帶入那筆連動時間。原廠實際日期須本人核對，不能只看附在名稱裡的日期。

已在原廠建好的鬧鐘可用「登記已在原廠建立的鬧鐘（不另建）」填入本人核對內容，避免重建。送交／登記紀錄不是原廠清單的讀回。Android 公開介面無法代填完整年月日、讀取／確認 vivo 私有鬧鐘清單或直接更新／刪除原廠那一筆。重新送交可能另建，會警示並保留歷次內容。來源或連動時間變更時需重新核對；舊原廠鬧鐘由本人調整。取消 Junba 只取消 Junba。

記事填名稱、內容、完整年月日時間，選「儲存並連動 Junba」或「儲存並開原廠」，先保存再排程，失敗仍保留。從已連動項目另送原廠會保留 Junba，兩邊都啟用可能各響一次。

## 日曆刷新、燈號與漏連動

允許日曆讀取、Junba 通知、精確鬧鐘並啟用日曆連動後，所選帳號／日曆的明確提醒時間預排入 Junba：未來約 90 天活動、本次最多 250 筆。開 App、手動刷新及系統允許的背景工作會更新，讀取的是手機已同步資料。

- 🔴 已排入 Junba；**原廠另需送交**。同活動有多個提醒時，卡片另顯示已覆蓋／缺漏筆數。
- 🟠 正在響鈴：App 與通知顯示來源；固定停止、延後 10／30／60 分鐘。
- ○ 未連動／已移除：顯示權限、本人移除、未知時間、超出範圍／上限等原因。
- 🔵 測試：走 Junba 相同排程與音效流程，3 秒後響 5 秒，不代表 Google 通知或原廠已建立成功。

生日／工作、全天或預設提醒若沒有提供確切時間，不猜測、不假亮。可從活動自行指定時間，或用「手動補入未顯示的工作／生日」建立本機記事並選 Junba／原廠。使用者自行移除的連動會記住，刷新不復活；Google 原活動、提醒及來源通知不改、不刪。

設定頁授權、日曆、測試、資料與版本均可收折。備份 version 8 保留原記事、連動、帳號選擇及新版原廠核對紀錄；不含原廠實際資料或 Google 原活動，尚無完整自動還原，不可據此直接解除安裝。

## 建置與下載

```sh
python3 tests/test_calendar_readonly.py -v
gradle --no-daemon --max-workers=2 assembleDebug assembleRelease testDebugUnitTest lintDebug
python3 tests/test_release_signing.py app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/apk/debug/app-debug.apk -v
```

GitHub Actions 成功後下載 `JunbaSmartReminder-R11-clock-debug-APK`，解壓安裝 `app-debug.apk`。debug 簽章不保證覆蓋舊版；衝突時先保留舊版及資料，固定正式簽章仍需 GitHub Secrets，見 [APK_SIGNING.md](docs/APK_SIGNING.md)。

本機 debug／release、107 項 JUnit、3 項 Google 唯讀合約、9 項簽章工具回歸通過，無跳過；Lint 0 錯誤／5 警告。vivo 原廠畫面、實際 Google 通知、鎖屏／背景響鈴需手機端驗收，沒有以單元測試代替真機結論。詳見 [R11 流程與驗收](docs/R11_CLOCK.md)，沿用 [R10 連動設計](docs/R10_LINKS.md)；R6–R10 文件為歷史，以 R11 及使用者最新決定為準。
