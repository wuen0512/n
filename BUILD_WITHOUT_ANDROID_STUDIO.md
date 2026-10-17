# 不使用 Android Studio，透過 GitHub 產生 APK

本專案附帶 `.github/workflows/build-apk.yml`，可由 GitHub Actions 在雲端編譯。

1. 註冊或登入 https://github.com/ 。
2. 建立新的私人 Repository，例如 `BatteryTracker`，不要勾選建立 README。
3. 上傳 `BatteryTracker` 資料夾**裡面**的全部檔案與資料夾到 Repository 根目錄，包含隱藏的 `.github/workflows/build-apk.yml`（GitHub 網頁 Upload files 可上傳解壓後的檔案與資料夾；注意保留路徑）。
4. 進入 Repository 的 Actions 分頁，選擇 `Build Android APK`，按 `Run workflow`。
5. 等待執行結束；成功時進入該次執行頁面，從 Artifacts 下載 `BatteryTracker-debug-apk`，解壓後得到 `app-debug.apk`。
6. 將 APK 傳到 Android 手機並開啟，依手機提示允許該來源安裝。

**注意：** 這是尚未在本環境編譯、實機驗證的專案。若 Actions 顯示紅色失敗，請把失敗步驟的 log 傳回來修正；不能保證一次編譯成功。請勿將私人憑證或個人資料放進公開 Repository。APK 是測試用 debug 版，不是商店簽署發行版。
