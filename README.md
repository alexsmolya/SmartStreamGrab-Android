# SmartStreamGrab Android — Phase 2 preview/download handoff slice

This bounded production slice extends `Share → extract → preview` with an explicit typed `preview → selected format → download handoff` state boundary. It accepts `ACTION_SEND` `text/plain` intents and manual URLs, then calls `youtubedl-android` through a small typed metadata boundary. It prepares a request for a future executor but does not download or persist anything.

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

Static manifest/source review: `STATICALLY_REASONED` (share target, bounded error state, background extraction, and ABI filters are present). Build and emulator validation depend on the local Android/JDK toolchain; record exact results in the handoff report. No physical Galaxy S24 is required.

## Current phase boundary

The `PreviewDownloadController` owns deterministic format selection and creates `DownloadRequest` only for a valid selected format and HTTP(S) source. No downloads, queue, history, cookies/login, browser automation, background work, transcoding, persistence, Media3 playback, or polished navigation are included. The next human-approved slice may add the real download executor around this request boundary.
