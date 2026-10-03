# SmartStreamGrab Android — version 0.3.1

This bounded production slice extends `Share → extract → preview` with an explicit typed `preview → selected format → download` path. It accepts `ACTION_SEND` `text/plain` intents and manual URLs, calls `youtubedl-android` through narrow typed boundaries, and writes completed media through Android's Downloads provider on modern Android.

## Backend decision

Selected `youtubedl-android:0.18.1`. Its upstream documentation provides a Maven AAR, `YoutubeDL.getInfo`, bundled yt-dlp/Python runtime, arm64 guidance, and an in-app yt-dlp update mechanism. The optional FFmpeg AAR is intentionally not included because this phase only resolves metadata/formats.

Rejected for this spike: the current `ffmpegkit-maintained/yt-dlp-android` Chaquopy/AAR integration. Its public documentation is promising and explicitly supports arm64, but it embeds roughly 60–80 MB of CPython, fixes yt-dlp at library build time, and had not been independently build/runtime verified in this workspace. Revisit it later if its packaging and update trade-offs become preferable.

The backend dependency remains pinned at `0.18.1` for reproducible builds. The app displays the embedded yt-dlp version and exposes a user-triggered update to the stable yt-dlp channel; failed updates leave the current embedded runtime available. The adapter converts the AAR metadata objects into `MediaPreview` and `MediaFormat`, preserving format id, extension, dimensions, codecs, size, and playable URL when exposed by the backend.

Version 0.3.1 keeps only combined video formats in the download list. yt-dlp can expose separate video-only and audio-only streams; this app does not mux them yet, so offering those choices would produce files that many players cannot open. The output is also checked for non-zero size and a readable Android content URI before the download is reported as complete.

## Build and run

Requires JDK 17+, Android SDK platform 35/build tools, and Gradle (or an Android Studio import). The tester APK is built for arm64 Android phones only:

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.smartstreamgrab.android/.MainActivity
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'https://vimeo.com/22439234' com.smartstreamgrab.android
```

## Validation status

The Phase 3 download flow was `EMPIRICALLY_TESTED` on a real Android phone; this does not claim validation of every Android API-level fallback or cancellation path.

### Physical-device runtime validation

`EMPIRICALLY_TESTED` on 2026-08-22 using a real Android phone:

- Debug APK installed and launched successfully.
- Shared TikTok URL: `https://vt.tiktok.com/ZSVPxEakR/`.
- yt-dlp metadata extraction succeeded; TikTok title, site, and thumbnail rendered.
- Eight resolved media formats were returned and a concrete format was selected.
- Download completed successfully through the Phase 3 yt-dlp executor.
- Result was published through Android Downloads/MediaStore.
- The UI reported `content://media/external/downloads/1000048094`.

The following remain `UNVERIFIED`: cancellation against a real process, the API 24–28 storage fallback, and broader device/source coverage.

The UI refresh on branch `ui/clean-download-screen` hides backend format IDs and raw metadata, collapses formats with identical user-facing labels, and starts downloads directly from each format button. This refresh still requires real-device validation; the validation above applies to the previous UI.

Version 0.3.1 adds a clear button to the link field, reduces the preview height and vertical spacing, and keeps `Open video` / `Open folder` in a bottom action bar after a successful download so the actions remain visible on compact phone screens. `Open video` now sends an explicit read grant and `ClipData` together with the MediaStore URI.

## Download implementation

`DownloadExecutor` validates the existing `DownloadRequest`, passes its selected format ID to `youtubedl-android`, reports bounded progress, and publishes the completed file through `MediaStore.Downloads` with `IS_PENDING` on Android 10+. On API 24–28 it uses the app-specific external Downloads directory to avoid obsolete broad storage permissions. Filenames are sanitized and each temporary execution uses a unique directory; pre-29 destination collisions receive a deterministic numeric suffix.

`DownloadExecutionController` models prepared, running, succeeded, and bounded failed states. It rejects duplicate starts and stale/reset requests. The backend adapter remains isolated from Compose state, and no queue or background orchestration is introduced.

## Current phase boundary

The download executor is synchronous from the application boundary and is launched on `Dispatchers.IO`; no queue or persistent history is added. Cookies/login, browser automation, WorkManager/background orchestration, playlist/batch download, transcoding, Media3 playback, polished navigation, and broad settings remain out of scope. The primary Phase 3 download path is physically validated; the unverified cases above remain outside the evidence claim.
