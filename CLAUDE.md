# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

NewAudiocast is a single-screen Android app (Kotlin + Jetpack Compose) that casts internet radio streams to Google Cast / Chromecast devices. The phone itself never plays audio; it only sends a stream URL to the Cast receiver and shows playback controls.

## Commands

```bash
./gradlew :app:assembleDebug                 # build debug APK
./gradlew :app:installDebug                  # install on connected device
./gradlew :app:testDebugUnitTest             # JVM unit tests
./gradlew :app:testDebugUnitTest --tests "com.wegrzyn.marcin.newaudiocast.ExampleUnitTest"   # single test class
./gradlew :app:connectedDebugAndroidTest     # instrumented tests (needs device/emulator)
./gradlew :app:lintDebug                     # Android lint

# Baseline profile (needs a connected device, API 33+ or rooted API 28+)
./gradlew :app:generateReleaseBaselineProfile
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest   # startup macrobenchmarks
```

`gradlew` is checked in without the executable bit; run it as `sh gradlew …` (or `chmod +x gradlew`).

Unit/instrumented tests are only the template examples; there is no real test coverage. Cast behavior can only be verified on a real device with a Chromecast.

## Build setup

- Two modules: `:app` and `:baselineprofile` (an `com.android.test` module targeting `:app`).
- Mixed Gradle DSLs: root `build.gradle.kts` / `settings.gradle.kts` and `baselineprofile/build.gradle.kts` are Kotlin DSL, but `app/build.gradle` is **Groovy**. Match the syntax of the file being edited.
- All dependency/plugin versions live in `gradle/libs.versions.toml` (version catalog).
- `:app` uses Java/JVM 17; `:baselineprofile` uses Java 11. compile/target SDK 37, minSdk 24.
- Release builds use R8 (`minifyEnabled`, `shrinkResources`). Classes referenced only by name (e.g. `CastOptionsProvider` from manifest `<meta-data>`) need a `-keep` rule in `app/proguard-rules.pro`.
- Generated baseline profiles are written to `app/src/release/generated/baselineProfiles/`.

## Architecture

All app code is in `app/src/main/java/com/wegrzyn/marcin/newaudiocast/`:

- **Cast wiring**: `CastOptionsProvider` is registered via `<meta-data>` in `AndroidManifest.xml` and configures the Cast framework to use the **Default Media Receiver** (no custom receiver app), with the Cast media session/notification enabled (lock-screen and system media controls). The notification actions are set to play/pause only: on Android 13+ the default "stop casting" action renders as a stray button in the system media controls. `MyApplication` only applies Material dynamic colors.
- **`MainViewModel`** (AndroidViewModel) owns all Cast state and exposes it as a single `StateFlow<PlayerUiState>` (`PlayerUiState.kt`, with `PlaybackStatus` enum). It obtains `CastContext` asynchronously and registers a `SessionManagerListener<CastSession>`. `attachSession()`/`detachSession()` are the single place where the `RemoteMediaClient.Callback` (playback status) and `Cast.Listener` (device volume) are registered/unregistered — keep it that way to avoid duplicate callbacks.
  - `radioCast()` loads a `MediaInfo` (`STREAM_TYPE_LIVE`, the station's `contentType`, name + image metadata). Without a session it stores the station as `pendingStation` and calls `onNoSession`; the station is played in `onSessionStarted`, and cleared if the device chooser is cancelled or the session fails to start.
  - `loadStation()`: tapping the station that is already loaded (matched by `MediaInfo.contentId == uri`) is a no-op while playing/buffering and resumes it when paused. When something else is playing it first calls `stop()` and loads in the result callback — loading over a live stream left the Default Media Receiver stuck in BUFFERING. `isSwitchingStation` makes `checkState()` ignore the intermediate IDLE so the belt doesn't flicker.
  - Returning to a previously played station can be slow on the receiver side. A cache-busting query parameter on the stream URL was tried and made things worse — don't reintroduce it.
  - The last played station is persisted in SharedPreferences (`player` / `last_station`) and shown as a quick-play button when connected but idle.
  - One-off UI events (e.g. `PlayerEvent.PlaybackFailed`, from a failed load or an `IDLE_REASON_ERROR` transition) go through the `events` Channel and are shown as a snackbar.
  - Volume/mute use the device volume (`CastSession.volume` / `isMute`); `MainActivity.dispatchKeyEvent` maps hardware volume keys to it while casting.
- **`MainActivity`** contains all Compose UI as member composables: a pinned 48dp `TopAppBar` holding the Cast button (`MediaRouteButton` via `AndroidView`), a `LazyVerticalGrid` of `RadioCard`s (`GridCells.Adaptive`, 2 columns in portrait, more in landscape), and a bottom `ControlBelt` overlay that is only visible when playing (`PlayerRow` + `VolumeRow`) or connected (`ConnectedRow`). The belt's measured height sizes the list's last spacer item so cards aren't hidden behind it. In landscape the belt puts `PlayerRow` and `VolumeRow` side by side, and the content area pads horizontal `safeDrawing` insets (side navigation bar / cutout). Tapping a station without a session opens `MediaRouteChooserDialog`. Station logos are loaded with Coil (including `SvgDecoder`).
- **Stations**: the station list is hardcoded in `Stations.stationsList` (`RadioStation(name, uri, img, page, contentType)`; `contentType` defaults to MP3, use `RadioStation.AAC`/`HLS` where needed; HLS with non-TS segments, e.g. packed `.aac`, also needs `hlsSegmentFormat`). The stream URL must be directly playable by the Default Media Receiver (for HLS the server must send CORS headers); verify candidate URLs with `curl` before adding them.
- Theme (`AppTheme`, `AppTypography`) is in `ui/theme/`.
