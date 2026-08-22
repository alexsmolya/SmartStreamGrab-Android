# SmartStreamGrab Android — Phase 0

Minimal Compose feasibility spike for the flow `Share → extract → preview`. It accepts `ACTION_SEND` `text/plain` intents and manual URLs, then calls `youtubedl-android` to resolve yt-dlp metadata and media formats. It does not download or persist anything.

## Backend decision

Selected `youtubedl-android:0.18.1`. Its upstream documentation provides a Maven AAR, `YoutubeDL.getInfo`, bundled yt-dlp/Python runtime, arm64 guidance, and an in-app yt-dlp update mechanism. The optional FFmpeg AAR is intentionally not included because this phase only resolves metadata/formats.

Rejected for this spike: the current `ffmpegkit-maintained/yt-dlp-android` Chaquopy/AAR integration. Its public documentation is promising and explicitly supports arm64, but it embeds roughly 60–80 MB of CPython, fixes yt-dlp at library build time, and had not been independently build/runtime verified in this workspace. Revisit it in Phase 1 if its packaging and update trade-offs become preferable.

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

## Limitations / Phase 1 boundary

No downloads, queue, history, cookies/login, browser automation, background work, transcoding, persistence, or polished navigation. Phase 1 should first harden backend version/update policy and format modeling, then add explicit preview/download handoff and lifecycle tests.
