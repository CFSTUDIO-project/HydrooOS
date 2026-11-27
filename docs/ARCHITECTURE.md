# Riset & arsitektur teknis — Hydrogen OS V1

**Tanggal pemeriksaan sumber: 26 November 2026.** Dokumen ini membedakan fakta dokumentasi resmi, pilihan implementasi, dan batasan yang belum diuji di perangkat.

## 1. Ringkasan riset Liquid Glass dan iOS 27

Dokumentasi Apple mendeskripsikan Liquid Glass sebagai material dinamis untuk kontrol dan navigasi yang memisahkan lapisan kontrol dari konten. Material beradaptasi terhadap latar di bawahnya dan digunakan secara selektif; area konten padat tetap mengutamakan keterbacaan. Liquid Glass bukan sinonim untuk blur atau panel semi-transparan.

Apple mengumumkan rilis platform iOS 27 pada 14 September 2026. Detail yang tidak tertera pada HIG/dokumentasi publik tidak diasumsikan sebagai fitur resmi iOS 27. Hydrogen menggunakan referensi kualitas material, kedalaman, dan hierarki, tetapi mempertahankan identitas, ikon, dan komposisi orisinal.

### Perbedaan teknik grafis

| Teknik | Efek | Relevansi |
|---|---|---|
| Alpha/translucency | Melihat warna di belakang panel | Bukan refraction |
| Frosted blur | Mengaburkan sampel latar | Berguna untuk konten padat, tetapi bukan kaca bening |
| Refraction/displacement | Menggeser koordinat sampel tekstur | Dipakai pada glass surface milik Hydrogen |
| Specular highlight | Pantulan terang terarah | Menambah bentuk material |
| Fresnel/rim response | Respons tepi meningkat pada sudut grazing | Menegaskan kontur |
| Environment mapping | Sampling tekstur lingkungan oleh objek 3D | Digunakan pada tetesan intro |
| PBR/Filament | Pipeline material dan lighting fisik lebih lengkap | Dievaluasi, tidak dipilih untuk paket V1 |

## 2. Feasibility matrix

| Requirement | Teknik/API | Dukungan | Risiko / batasan |
|---|---|---|---|
| Android Home launcher | ACTION_MAIN + CATEGORY_HOME + DEFAULT; RoleManager.ROLE_HOME | Didukung | Pemilihan harus disetujui pengguna dan dapat berbeda antar-OEM |
| Daftar aplikasi | LauncherApps + manifest <queries> launcher intent | Didukung | Package visibility / work profile dan app disabled membatasi hasil |
| App drawer/search | Compose lazy grid/list + filter label/package | Didukung | Kategori pintar tidak disediakan karena tidak ada klasifikasi terverifikasi |
| Android widgets | AppWidgetHost, AppWidgetManager, picker/configuration | Didukung | Layout, resize, konfigurasi dan provider behavior bergantung widget |
| Wallpaper custom | Android Photo Picker + persisted URI | Didukung | URI/provider bisa membatasi persistensi; error harus ditangani |
| Wallpaper sistem | WallpaperManager.setBitmap/setStream + SET_WALLPAPER | Didukung | OEM dapat memiliki perilaku/konfirmasi tambahan |
| Glass sampling | AGSL RuntimeShader API 33+ + BitmapShader milik launcher | Didukung pada scene sendiri | Bukan akses ke pixel aplikasi pihak ketiga / System UI |
| Glass fallback | Alpha/tint/edge highlight API 26–32 | Fallback | Tidak boleh dinamai refraction |
| Intro droplet | OpenGL ES 2.0 indexed mesh + environment texture + Fresnel-like shader | Layak | Bukan Filament PBR; harus dites di GPU/OEM nyata |
| iOS 27 visual reference | Apple HIG / Liquid Glass overview / newsroom | Referensi desain | Hindari mengarang detail yang tidak dipublikasikan |
| Update metadata | HTTPS JSON + versionCode | Opsional | Tidak ada endpoint default; tidak ada updater otomatis |
| Notification features | NotificationListenerService / POST_NOTIFICATIONS | Ditunda | Tidak diperlukan Home core; izin tidak diminta |
| Folder / reorder | Persisted placement model + Compose drag gestures | Belum diimplementasikan | Jangan tampilkan sebagai fitur aktif |
| Weather | Provider API + consent/location strategy | Tidak disertakan | Tidak ada data cuaca yang boleh dipalsukan |

## 3. Arsitektur terpilih

- Kotlin native + Jetpack Compose: screen state, navigation, wallpaper collection, settings, drawer, dialogs, adaptive layout.
- Android View bridge untuk AGSL surface, OpenGL ES scene dan AppWidgetHostView.
- Preferences DataStore untuk preferensi persisten.
- LauncherApps untuk installed launchable activities, icon, label dan launch API.
- RoleManager untuk permintaan Home role melalui UI sistem.
- Photo Picker untuk memilih gambar pribadi tanpa meminta akses semua file.
- WallpaperManager untuk permintaan ganti wallpaper sistem.
- AppWidgetHost/AppWidgetManager untuk widget Android sungguhan.
- AGSL RuntimeShader API 33+ dengan input BitmapShader dari wallpaper yang dimiliki Hydrogen.
- OpenGL ES 2.0 custom renderer untuk droplet singkat; tidak memakai video, GIF atau lingkaran animasi.

### Alternatif yang dievaluasi

**Filament:** PBR pipeline matang dengan material/lighting lebih lengkap, tetapi native library, ABI, ukuran dan lifecycle renderer meningkatkan biaya untuk intro singkat. Dipilih mesh OpenGL ES 2.0 custom agar ringan dan terkontrol. Hasilnya bukan full PBR; hal itu diungkapkan.

**RenderEffect / blur:** cocok untuk blur permukaan, tetapi blur saja tidak menggeser sampel tekstur dan tidak memenuhi definisi displacement. Tidak dipakai sebagai klaim refraction.

**Compose-only translucent panel:** mudah dibuat dan kompatibel, tetapi hanya alpha/tint. Dipakai sebagai fallback yang diberi label jelas, bukan sebagai mode refraction.

**QUERY_ALL_PACKAGES:** tidak diperlukan untuk kebutuhan saat ini. Manifest menggunakan query intent yang menarget activity launcher.

## 4. Material shader: apa yang benar-benar terjadi

Pada API 33+, LiquidGlassSurface membuat RuntimeShader AGSL. Input uBackdrop diisi BitmapShader dari wallpaper launcher; shader mengevaluasi koordinat p + displacement, lalu menambahkan cool tint, Fresnel-like rim, specular highlight dan corner mask. Tekstur ini adalah bitmap yang secara sah dimiliki aplikasi.

Tidak ada screenshot diam-diam, Accessibility Service, overlay, atau sampling lintas proses. Surface tidak dapat membiaskan layar aplikasi lain. Pada API 26–32 atau ketika shader tidak dapat dibuat/digambar, sistem menggunakan fallback transparan dengan edge lighting; fallback tersebut tidak mengandung refraction.

## 5. Android/OEM constraints

1. Launcher biasa tidak mengganti firmware, lock screen, status bar, notification shade atau seluruh System UI.
2. Android sendiri mengelola default Home app. Hydrogen hanya mendeklarasikan intent filter dan meminta pemilihan melalui RoleManager/Settings.
3. Package visibility membatasi daftar paket. Query ditargetkan pada intent LAUNCHER; tidak ada klaim bahwa semua package/perangkat profile akan selalu terlihat.
4. Widget dikelola oleh host/provider masing-masing. Widget dapat memerlukan konfigurasi, tidak mendukung resize, atau menampilkan ukuran yang berbeda.
5. Android Photo Picker memberi akses pada URI terpilih, bukan jaminan file tetap tersedia selamanya. Hydrogen meminta persistable permission dan menangani kegagalan.
6. App launch animation dipengaruhi Android, aplikasi target dan OEM. Hydrogen tidak mengendalikan animasi internal aplikasi pihak ketiga.
7. Refresh rate, GPU, thermal limit dan kebijakan baterai OEM menentukan frame pacing nyata. Tidak ada klaim 60/120 FPS tanpa benchmark.
8. Permission POST_NOTIFICATIONS hanya relevan untuk notifikasi yang dikirim aplikasi sendiri; ia bukan akses membaca notifikasi pihak lain.
9. Notification Listener adalah special access terpisah. Fitur tersebut tidak digunakan di V1.

## 6. Storyboard intro 6.5 s

| Waktu | Gerak |
|---|---|
| 0.0–0.7 s | Midnight atmosphere muncul, logo masuk dengan fade/scale ringan |
| 0.7–2.8 s | Droplet mesh jatuh dengan kurva easing yang dipercepat dan rotasi kecil |
| 2.8–3.6 s | Kontak visual: tetesan menyusut/memudar, ripple ring melebar |
| 3.6–5.2 s | Lockup Hydrogen OS dan V1 terungkap |
| 5.2–6.5 s | Komposisi diam sejenak lalu lanjut onboarding |
 
Render loop hanya hidup selama intro terlihat. Activity pause/dispose mempause GLSurfaceView. Pengguna dapat melewati intro; replay tersedia pada Settings.

## 7. Dependency/build baseline yang diteliti

- Android Gradle Plugin 9.5.0; Gradle 9.6.0; Java 17.
- Kotlin Compose plugin 2.4.21.
- Compose BOM 2025.12.00.
- AndroidX Activity Compose 1.13.0, Core 1.17.0, Lifecycle 2.10.0, DataStore 1.3.0.
- compileSdk 36, targetSdk 35, minSdk 26.

Compose BOM 2025.12.00 dipilih agar dependency Compose tidak mensyaratkan compileSdk 37; CI menargetkan Android SDK 36. Versi dependency harus disinkronkan kembali dengan hasil CI yang benar-benar lulus. Workspace ini tidak memiliki Android SDK/Gradle untuk eksekusi build lokal.

## 8. Update feed (opsional)

Build default tidak mempunyai endpoint. About menunjukkan **Not Found** dan menyatakan feed belum dikonfigurasi. Jika dikonfigurasi, feed wajib HTTPS dengan format:

```json
{
  "version": "1.1.0",
  "versionCode": 2,
  "date": "2026-12-01",
  "changelog": "Release notes"
}
```

Perbandingan menggunakan numeric versionCode terhadap BuildConfig.VERSION_CODE. Network/HTTP errors tidak disamarkan sebagai Not Found. Tidak ada unduh/pemasangan otomatis.

## 9. Sumber primer

- Apple: [Liquid Glass overview](https://developer.apple.com/documentation/TechnologyOverviews/liquid-glass)
- Apple: [HIG Materials](https://developer.apple.com/design/human-interface-guidelines/materials)
- Apple: [iOS 27 / platform releases, 14 September 2026](https://www.apple.com/newsroom/2026/09/major-updates-for-apples-software-platforms-are-now-available/)
- Android: [Intent / CATEGORY_HOME](https://developer.android.com/reference/android/content/Intent)
- Android: [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager)
- Android: [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps)
- Android: [Package visibility](https://developer.android.com/training/package-visibility)
- Android: [RuntimeShader / AGSL](https://developer.android.com/reference/android/graphics/RuntimeShader)
- Android: [AppWidgetHost](https://developer.android.com/reference/android/appwidget/AppWidgetHost)
- Android: [App widget host guide](https://developer.android.com/develop/ui/views/appwidgets/host)
- Android: [WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager)
- Android: [Photo Picker](https://developer.android.com/training/data-storage/shared/photopicker)
- Android: [Render performance / jank](https://developer.android.com/topic/performance/issues/render)
- Android: [Compose performance](https://developer.android.com/develop/ui/compose/performance)
- Filament: [Material guide](https://google.github.io/filament/main/materials.html)
- Android build: [AGP 9.5.0 release notes](https://developer.android.com/build/releases/agp-9-5-0-release-notes)
- Kotlin: [Release notes](https://kotlinlang.org/docs/releases.html)
- Compose: [BOM release mapping](https://developer.android.com/develop/ui/compose/bom)
