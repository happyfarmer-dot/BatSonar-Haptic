# 🦇 BatSonar Haptic

> **FMCW 超聲波避障與 Samsung Galaxy S24 Ultra X 軸線性馬達觸覺聲納**

基於純 Kotlin 與 Jetpack Compose 實作的 Android 原生超聲波避障導引系統。

---

## 📱 核心特色

1. **主動 FMCW 超聲波雷達**：
   - 48,000 Hz 取樣率。
   - 每 50 ms 發射一段 40 ms 線性調頻超聲波（17,000 Hz → 21,000 Hz，頻寬 B = 4,000 Hz），附 2 ms 漢寧窗淡入淡出消噪。
   - `MediaRecorder.AudioSource.UNPROCESSED` 無破壞原生錄音同步取樣。
2. **純 Kotlin 差頻解調演算法**：
   - 時域混頻解調（Dechirping）搭配 2048 點 Radix-2 FFT。
   - 1 秒機身直達波靜態串擾消除基線（環境校準）。
   - 0.3 m ～ 3.0 m 拋物線子頻點精準距離與 SNR 換算。
3. **S24 Ultra X 軸線性馬達（LRA）觸覺回饋**：
   - `VibratorManager` 與 `VibrationEffect.createOneShot` 動態調變。
   - 距離越近頻率越急促（低至 95 ms 間隔、振幅 255）。
   - 支援靜音純震動模式與 TextToSpeech (TTS) 語音距離播報。
4. **無障礙高對比 UI**：
   - 仿潛艇聲納 120° 扇形雷達掃描 Canvas 圖。
   - 58sp 超大字體即時距離讀數、一鍵校準與 1m–3m 探測距離滑桿。

---

## 📥 APK 下載安裝

前往 [Releases 頁面](../../releases) 下載最新的 `app-debug.apk`，在 S24 Ultra 上安裝即可體驗。
