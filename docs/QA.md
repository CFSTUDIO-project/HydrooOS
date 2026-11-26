# Hydrogen OS V1 — QA matrix

**Status date:** 26 November 2026. No Android emulator/device is attached, and this workspace has no Android SDK or working Gradle installation. Compilation and device checks therefore remain NOT TESTED until CI and physical-device QA are completed.

| Area | Check | Status | Evidence / note |
|---|---|---|---|
| Source | Project tree and static source inspection | PASS | Source package and XML/SVG validation |
| Build | assembleDebug | NOT TESTED | Android SDK/Gradle unavailable locally |
| Build | testDebugUnitTest | NOT TESTED | Included in GitHub Actions |
| Home | CATEGORY_HOME intent filter | PASS (static) | AndroidManifest.xml |
| Home | User-selected RoleManager Home flow | PASS (static) | MainActivity.kt |
| Home | Home key returns to Hydrogen after selection | NOT TESTED | Physical device required |
| Apps | LauncherApps discovery + narrow launcher query | PASS (static) | LauncherData.kt + manifest |
| Apps | Launch real installed apps | NOT TESTED | Runtime/OEM required |
| Drawer | Search, grid/list, sort | PASS (static) | HomeAndDrawer.kt |
| Favorites | Persisted package favorites | PASS (static) | Preferences DataStore |
| Folders/reorder | Folder create/edit, drag reorder | NOT IMPLEMENTED | Not exposed as active control |
| Wallpaper | Twelve procedural artworks | PASS (static) | LauncherData.kt + WallpaperArt |
| Wallpaper | Photo Picker/custom wallpaper URI | PASS (static) | SettingsScreens.kt |
| Wallpaper | Persist after process restart | PASS (static) | DataStore preference |
| Wallpaper | Apply system wallpaper | NOT TESTED | WallpaperManager device test |
| Widgets | AppWidgetHost picker/host/config flow | PASS (static) | MainActivity.kt |
| Widgets | Frame height and size hint | PASS (static) | Host view + updateAppWidgetSize |
| Widgets | Provider-specific sizing/config | NOT TESTED | Depends on installed providers |
| Glass | AGSL RuntimeShader and wallpaper displacement code | PASS (static) | LiquidGlassSurface.kt, API 33+ guard |
| Glass | Visual refraction / stability on device | NOT TESTED | GPU/API test required |
| Glass fallback | API 26–32 edge-lit material | PASS (static) | Separate non-refraction fallback |
| Intro | OpenGL ES 2.0 tear mesh / environment / ripple code | PASS (static) | DropletIntroView.kt |
| Intro | 6.5 sec timing, skip/replay and GPU smoothness | NOT TESTED | Device timing and GPU QA |
| Settings | Persisted theme/accent/grid/icon/dock/drawer/motion | PASS (static) | DataStore bindings |
| About | Not Found without configured feed | PASS (static) | Empty UPDATE_FEED_URL default |
| About | Live endpoint success/error conditions | NOT TESTED | No update server configured |
| Privacy | No Accessibility/overlay/usage/listener permissions | PASS (static) | Manifest inspection |
| Performance | Frame-time, jank, memory, battery, thermal | NOT TESTED | Profileable device build needed |
| Visual QA | Portrait/landscape/insets/font-scale/clipping | NOT TESTED | Device screenshots required |

## Required device QA

1. Install debug APK and open Hydrogen OS.
2. Choose Hydrogen from Android's Home app chooser, then press Home.
3. Launch apps, filter the app drawer, change sort and favorites.
4. Add a widget provider with configuration, leave Home and return, inspect its lifecycle.
5. Apply a built-in and custom wallpaper; use the system wallpaper action; relaunch.
6. Inspect AGSL glass on API 33+ and fallback on API 26–32 if available.
7. Replay intro, skip it, and test Reduce Motion / Battery Saver.
8. Test portrait, landscape, font scale, display size, insets and scroll.
9. Measure frame timing, memory, thermal and idle battery; report the device and build.
10. Switch to the manufacturer's Home app through Android Settings.
