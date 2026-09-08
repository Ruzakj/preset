# RIC Preset

Aplikasi Android ringan untuk menerapkan preset Lightroom `.xmp` tanpa membutuhkan Lightroom.

## v0.1
- Preset `.xmp` di folder `presets/` otomatis menjadi preset bawaan APK.
- Import satu atau banyak `.xmp` dari Android file picker.
- Import `.zip` berisi banyak preset sekaligus.
- Preview ringan (maks. 1600 px) dan export JPEG resolusi asli.
- Preset Strength 0–100%.
- Tekan dan tahan foto untuk melihat Before.
- Parser mendukung parameter Lightroom/Camera Raw umum: Exposure, Contrast, Highlights, Shadows, Whites, Blacks, Temperature/Tint, Vibrance/Saturation, HSL, Dehaze, color grading, vignette, dan grain.
- Tidak memakai OpenCV, AI model, Compose, atau library image editor besar.

> Catatan: engine Adobe Camera Raw bersifat proprietary. Parameter XMP yang didukung diterapkan secara kompatibel/visual approximation; AI Masking, Adobe Profile tertentu, lens correction proprietary, dan fitur Adobe adaptif tidak akan identik 100%.

## Build
Setiap push ke `main` menjalankan GitHub Actions **Build RIC Preset APK**. APK debug tersedia sebagai workflow artifact `RIC-Preset-v0.1-debug`.
