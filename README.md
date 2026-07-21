# Subly+

Overlay your own `.srt` subtitles on top of a streaming player and keep them
**automatically in sync** with playback — even after you seek or scrub.

Subly+ is a small, native Android app. You pick a subtitle file, start the
overlay, and the subtitles are drawn on top of the video and locked to the
player's real position.

> Not affiliated with, endorsed by, or connected to Netflix or any streaming
> service. "Netflix" is a trademark of its respective owner.

## How it works

Two Android capabilities make this possible (and they are Android-only — this
approach is not possible on iOS):

1. **Overlay window** — a foreground service draws the subtitle text and a small
   floating control in a `TYPE_APPLICATION_OVERLAY` window over other apps
   (`SYSTEM_ALERT_WINDOW` permission). The player uses `FLAG_SECURE`, which
   blocks screenshots but does **not** block an overlay drawn on top.

2. **Position via AccessibilityService** — the player exposes its seek bar in
   the accessibility tree with a `RangeInfo` that reports the absolute playback
   position in milliseconds. An `AccessibilityService` reads it and the app
   locks the subtitle clock to that position. Between reads, a monotonic clock
   interpolates; any seek/scrub snaps the subtitles to the new position.

No DRM is touched and no video is captured — only the app's own text is drawn,
and only the public accessibility position is read.

## Modules

- `:core` — pure Kotlin/JVM, unit-tested: SRT parsing, charset detection
  (UTF-8 → Windows-1254 fallback), timeline lookup, the playback clock.
- `:app` — Android: the overlay foreground service, the floating control,
  the accessibility sync service, and the setup screen.

## Build

Requires a JDK 17+ (the Android Studio bundled JBR works well) and the Android
SDK.

```bash
# point Gradle at a JDK 17+
export JAVA_HOME="/path/to/jdk-17"

# tell Gradle where your Android SDK is
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# run the unit tests
./gradlew :core:test

# build + install a debug APK on a connected device
./gradlew :app:installDebug
```

- `minSdk 28`, `targetSdk 34`, `compileSdk 34`.
- A release/obfuscated build: `./gradlew :app:assembleRelease` (R8 enabled;
  provide a keystore via `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` /
  `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` gradle properties to sign it).

## Using it

1. Open the app, tap **Choose subtitle (.srt)** and pick a file.
2. Tap **Start overlay** — grant the "draw over other apps" permission if asked.
3. Enable the accessibility service (**Settings → Accessibility → Subly+**) so
   the subtitles can auto-sync to the player.
4. Play your video; tap the screen once so the player controls appear, and the
   subtitles lock to the current position.

The floating control lets you nudge the timing by ±0.1s and close the overlay;
it fades away when idle and returns on touch.

## A note on the accessibility permission

Subly+ uses an `AccessibilityService` to read the player's playback position.
This is the only way to auto-sync on stock Android. It reads **only** the
seek-bar position of the configured player and sends nothing off the device.
Because this is not a disability-assistance use of the accessibility API,
distributing on Google Play would require an explicit in-app disclosure and
review; the project is intended for sideloading / personal use.

## License

MIT — see [LICENSE](LICENSE).
