# GitHub Actions 固定 APK 簽章

## 本次 R5 / R6 安裝問題

兩個使用者提供的 APK 壓縮結構與 APK 簽章驗證均通過，套件都是 `tw.junba.reminder`，versionCode 分別為 5、6。憑證 SHA-256 不同：

| 版本 | 憑證 SHA-256 |
| --- | --- |
| R5 | `afce87fa14a8ccb571345fb6297c43e23e38015193f2ab91df073c8467bf80ac` |
| R6 | `e02a7120141007b08fc8aacdc987ad11df43cdcba177d7859389374ec4b58d7d` |

若手機目前安裝的是這份 R5，這份 R6 無法直接覆蓋；重新命名、調高版本號或清除安裝程式快取都無法改變簽章相容性。兩張憑證都叫 Android Debug，不代表是同一把金鑰。

現有 debug workflow 僅上傳 APK，沒有保留 `debug.keystore` 的步驟；GitHub hosted runner 是暫時的，未另外保存的私鑰不能假設還在。只有當時 runner 的工作目錄備份、另外保管的 keystore 等才可能保留原金鑰；單靠 APK 內的公開憑證無法還原私鑰。本輪 GitHub API 存取被拒絕，沒有確認歷史附件或快取中是否另有備份，不能斷言所有備份都不存在。

## 保留資料的必要條件

- 若找回原 R5 金鑰，可用它簽署新版，再比對憑證指紋是否完全符合。金鑰及密碼請留在本機安全位置或 GitHub Secrets，不要上傳聊天、提交 repo 或放進 APK 附件。
- 若原金鑰無法找回，新金鑰無法直接覆蓋現有 R5。需先完成可驗證的完整備份、還原與移轉驗證，再由使用者決定下一步；本流程不執行解除安裝、清除資料或手機安裝。
- 目前 App 的匯入仍會取代本機資料，且匯出未涵蓋所有設定。不能把既有匯出／匯入當作已驗證的完整安全移轉方案。

## 新的固定簽章流程

`.github/workflows/release-apk.yml` 提供手動執行的 **Build Signed Android APK**。每次使用同一份保管好的 keystore，驗證預期憑證後才上傳 APK。原 debug workflow 保留供開發使用，名稱與執行日誌已標示其簽章限制。

在 GitHub 儲存庫 **Settings → Secrets and variables → Actions** 設定：

| 類型 | 名稱 | 用途 |
| --- | --- | --- |
| Repository secret | `JUNBA_KEYSTORE_BASE64` | 固定 keystore 的完整 Base64；這是秘密，不可公開 |
| Repository secret | `JUNBA_STORE_PASSWORD` | keystore 密碼 |
| Repository secret | `JUNBA_KEY_ALIAS` | 金鑰別名 |
| Repository secret | `JUNBA_KEY_PASSWORD` | 私鑰密碼 |
| Repository variable | `JUNBA_EXPECTED_CERT_SHA256` | 該金鑰憑證的 SHA-256，64 位十六進位，可包含冒號 |

這些是 **GitHub Actions** 設定，不是 Codex 雲端環境變數。缺少設定會明確停止，不會產生另一把臨時 debug 金鑰。金鑰只在 runner 暫存目錄解碼，結束時刪除；artifact 只列出 APK、檔案 checksum 與公開憑證指紋。

若原 R5 金鑰仍在，應先以它設定上述欄位，並確認 SHA-256 等於 R5 指紋。若建立新金鑰，請先認知它無法直接更新 R5；不要把 R5 的公開指紋填給另一把金鑰，流程會拒絕。

## 在自己的 Windows 電腦建立新金鑰（僅在原金鑰無法取得並已決定移轉時）

需要 Java 17 的 `keytool`。以下命令只在使用者本機執行，不要把私鑰放進專案。密碼由 keytool 互動詢問，不要寫在命令或聊天：

```powershell
$signingDir = Join-Path $env:USERPROFILE 'JunbaSigning'
New-Item -ItemType Directory -Force $signingDir | Out-Null
$keystorePath = Join-Path $signingDir 'junba-release.jks'
if (Test-Path $keystorePath) { throw '金鑰已存在，請保留原檔，不要重新產生。' }
keytool -genkeypair -keystore $keystorePath -storetype JKS -alias junba-release -keyalg RSA -keysize 3072 -validity 10000
keytool -list -v -keystore $keystorePath -alias junba-release
```

將最後顯示的公開 `SHA256` 憑證指紋填入 variable；alias 為 `junba-release`，密碼填入上述 secrets。keystore Base64 可在自己的電腦複製到剪貼簿，直接貼至 GitHub secret 輸入欄位，儲存後清空剪貼簿：

```powershell
[Convert]::ToBase64String([System.IO.File]::ReadAllBytes($keystorePath)) | Set-Clipboard
# 在 GitHub 安全輸入欄位儲存 JUNBA_KEYSTORE_BASE64 後：
Set-Clipboard -Value ''
```

保留獨立且安全的金鑰及密碼備份；不要只依賴會消失的 runner 或公開 artifact。後續更新沿用同一把金鑰與套件名稱，並按版本管理提升 versionCode。

## 下載位置

在包含此 workflow 的分支完成簽章設定並成功執行後，才會出現可下載的正式附件：

**Actions → Build Signed Android APK → 成功的執行紀錄 → Artifacts → JunbaSmartReminder-R8-controls-signed-release-APK**。

下載 ZIP 後取出 `app-release.apk`。附件還包含 `SHA256SUMS.txt` 與 `signing-certificate.txt`，可比對檔案與憑證。現階段尚未設定正式秘密、未執行遠端 workflow，也沒有發布新版；本機驗證的測試金鑰不會交付給使用者。

## 本機驗證

```bash
gradle --no-daemon --max-workers=2 assembleDebug assembleRelease testDebugUnitTest lintDebug
python3 tests/test_release_signing.py app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/apk/debug/app-debug.apk -v
```

簽章回歸測試只使用臨時金鑰，涵蓋重複使用同一金鑰、缺少設定、錯誤 Base64、錯誤密碼、錯誤憑證、debug APK 拒絕、既有檔案保護與暫存清理。測試成功不代表手機 R5 可直接升級，也不代表遠端 Actions 已執行。

本輪本機結果：23 項時間規則檢查及 9 項簽章回歸測試通過，`assembleDebug assembleRelease testDebugUnitTest lintDebug` 成功；Lint 仍有 12 個警告、0 個錯誤。正式簽章設定與手機移轉驗證尚未完成。
