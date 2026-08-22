# SmartStreamGrab Android — Phase 3 download executor slice

This bounded production slice extends `Share → extract → preview` with an explicit typed `preview → selected format → download` path. It accepts `ACTION_SEND` `text/plain` intents and manual URLs, calls `youtubedl-android` through narrow typed boundaries, and writes completed media through Android's Downloads provider on modern Android.

## Backend decision

Selected `youtubedl-android:0.18.1`. Its upstream documentation provides a Maven AAR, `YoutubeDL.getInfo`, bundled yt-dlp/Python runtime, arm64 guidance, and an in-app yt-dlp update mechanism. The optional FFmpeg AAR is intentionally not included because this phase only resolves metadata/formats.

Rejected for this spike: the current `ffmpegkit-maintained/yt-dlp-android` Chaquopy/AAR integration. Its public documentation is promising and explicitly supports arm64, but it embeds roughly 60–80 MB of CPython, fixes yt-dlp at library build time, and had not been independently build/runtime verified in this workspace. Revisit it later if its packaging and update trade-offs become preferable.

The backend dependency remains pinned at `0.18.1` for reproducible builds. Updating yt-dlp is an intentional dependency/version change, not an implicit runtime mutation. The adapter converts the AAR metadata objects into `MediaPreview` and `MediaFormat`, preserving format id, extension, dimensions, codecs, size, and playable URL when exposed by the backend.

## Build and run

Requires JDK 17+, Android SDK platform 35/build tools, and Gradle (or an Android Studio import). From this directory:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.smartstreamgrab.android/.MainActivity
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'https://vimeo.com/22439234' com.smartstreamgrab.android
```

The selected native payload is constrained to `arm64-v8a` and `x86_64`.

## Validation status

Build and unit/lint validation are recorded in the Phase 3 handoff. The primary download flow has now also been `EMPIRICALLY_TESTED` on a real Android phone; this does not claim validation of every Android API-level fallback or cancellation path.

### Physical-device runtime validation

`EMPIRICALLY_TESTED` on 2026-08-22 using a real Android phone:

- Debug APK installed and launched successfully.
- Shared TikTok URL: `https://vt.tiktok.com/ZSVPxEakR/`.
- yt-dlp metadata extraction succeeded; TikTok title, site, and thumbnail rendered.
- Eight resolved media formats were returned and a concrete format was selected.
- Download completed successfully through the Phase 3 yt-dlp executor.
- Result was published through Android Downloads/MediaStore.
- The UI reported `content://media/external/downloads/1000048094`.

The following remain `UNVERIFIED`: cancellation against a real process, the API 24–28 storage fallback, and broader device/source coverage. A non-blocking UI defect was observed but intentionally not fixed in this phase: format rows expose Java object strings such as `com.yausername.youtubedl_android.mapper.VideoFormat@...` instead of a polished format label.

## Download implementation

`DownloadExecutor` validates the existing `DownloadRequest`, passes its selected format ID to `youtubedl-android`, reports bounded progress, and publishes the completed file through `MediaStore.Downloads` with `IS_PENDING` on Android 10+. On API 24–28 it uses the app-specific external Downloads directory to avoid obsolete broad storage permissions. Filenames are sanitized and each temporary execution uses a unique directory; pre-29 destination collisions receive a deterministic numeric suffix.

`DownloadExecutionController` models prepared, running, succeeded, and bounded failed states. It rejects duplicate starts and stale/reset requests. The backend adapter remains isolated from Compose state, and no queue or background orchestration is introduced.

## Current phase boundary

The download executor is synchronous from the application boundary and is launched on `Dispatchers.IO`; no queue or persistent history is added. Cookies/login, browser automation, WorkManager/background orchestration, playlist/batch download, transcoding, Media3 playback, polished navigation, and broad settings remain out of scope. The primary Phase 3 download path is physically validated; the unverified cases above remain outside the evidence claim.
