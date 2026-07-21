# 🎵 Android Reclip

**Android Reclip** is a modern, lightweight Android application built with **Jetpack Compose** and **Kotlin**. It continuously records audio in a rolling background buffer—allowing you to capture and save audio clips from the recent past on demand. Never miss a moment again!

---

## ✨ Features

* **Rolling Audio Buffer:** Continuously captures audio in a background circular buffer (defaulting to the last 2 minutes).
* **Instant Clipping:** Tap the primary **CLIP** button to pull the audio buffer directly into an editor.
* **Modern UI:** Built using Material 3 guidelines, supporting clean card layouts, dynamic timestamps, and smooth animations.
* **Interactive Waveform Scrubber:** Tap or drag your finger smoothly along the audio timeline to seek and preview audio clips in real time.
* **Flexible Storage:** Choose to save audio clips internally or pick a custom folder using the Android Storage Access Framework.
* **Easy Sharing:** Export and share saved `.wav` clips directly to other applications via the native Android share sheet.
* **Theme Customization:** Full support for Light, Dark, and System Default themes.

---

## 🛠️ Built With

* **Language:** [Kotlin](https://kotlinlang.org/)
* **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
* **Architecture:** Foreground Services, Service Binding, Coroutines
* **Audio Engine:** `AudioRecord` (PCM 16-bit recording) & `MediaPlayer` (playback)

---

## 📱 Screenshots

<table>
  <tr>
    <td align="center" width="30%">
      <img src="https://github.com/user-attachments/assets/66316754-b75d-40f9-89d7-d3781245d050" width="250" alt="Home Dashboard" />
      <br />
      <sub><b>Home Dashboard</b></sub>
    </td>
    <td width="5%"></td>
    <td align="center" width="30%">
      <img src="https://github.com/user-attachments/assets/94bee342-6e24-4aa1-b316-824ac7eec5a2" width="250" alt="Audio Editor" />
      <br />
      <sub><b>Audio Editor</b></sub>
    </td>
    <td width="5%"></td>
    <td align="center" width="30%">
      <img src="https://github.com/user-attachments/assets/a7ff356d-6818-42bb-9694-5978148d5096" width="250" alt="App Settings" />
      <br />
      <sub><b>App Settings</b></sub>
    </td>
  </tr>
</table>

---

## 🚀 Installation & Build

Go to Releases and download the latest version.

---

## 🔒 Permissions Required

This app requires the following runtime permissions to function correctly:

* `RECORD_AUDIO`: Required to record background audio for the rolling buffer.
* `POST_NOTIFICATIONS`: Required on Android 13+ to display the persistent foreground service notification.

---

## 👤 Author

**Timothy Lockley**

---

## 📄 License

© 2026 Timothy Lockley. All rights reserved.
