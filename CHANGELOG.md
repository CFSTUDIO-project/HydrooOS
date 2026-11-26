# Changelog

## V1 · Initial source package

- Establish a third-party Android Home launcher with an explicit Android Home-role selection flow.
- Discover and launch installed apps using LauncherApps.
- Add app drawer search, grid/list mode, sorting, app information and favorites.
- Add persistent appearance, accent, grid, icon, dock and motion preferences.
- Add 12 locally generated Hydrogen wallpaper compositions and Android Photo Picker support.
- Add optional Android system wallpaper application through WallpaperManager.
- Add real AppWidgetHost picker, configuration and size-hint integration.
- Add API 33+ AGSL displacement of the launcher's own wallpaper texture with a translucent compatibility fallback.
- Add a short-lived OpenGL ES 2.0 3D tear mesh, environment texture and ripple for first-run/replay.
- Add About and an honest Not Found state when no release metadata endpoint is configured.
- Add GitHub Actions build/test workflow and documentation.

### Known V1 boundaries

- Folder create/edit and icon drag-to-reorder are not implemented.
- No weather provider, notification badges, Notification Listener or cross-app notification reading.
- No release feed or automatic update installer is configured.
- Full Filament PBR, global display sampling and physical-device performance validation remain out of scope for this initial package.
