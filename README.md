# MaterialTube

A Material 3 Expressive YouTube client for Android.

- Jetpack Compose UI with dynamic colour, light/dark themes and spring animations
- NewPipeExtractor backend: no Google account, no ads
- Media3 ExoPlayer with background play and picture-in-picture
- SponsorBlock, DeArrow and Return YouTube Dislike built in

## Getting the APK

Every push to `main` builds a new APK with GitHub Actions.
Open the **Releases** page on your phone and install `MaterialTube.apk` from the newest build.

Every build is also run on an Android emulator; screenshots and logs land on the `ci-shots` branch.

## Tests

- `./gradlew testDebugUnitTest`: unit tests for formatting, captions, links, stream picking,
  error messages and YouTube response parsing. Runs on the computer in a few seconds.
- `./gradlew connectedDebugAndroidTest`: UI tests that drive the real app on a phone or emulator:
  navigation, settings, links and shortcuts, the mini player, rotation, and (with a working
  connection) feeds, playback, search, comments and Shorts.

CI runs the unit tests on every push, and the UI tests that don't need YouTube itself on an emulator,
since YouTube blocks GitHub's servers. Run the full set on a home connection before a release.

## Roadmap

- v1: browse, search, channels, subscriptions, player, SponsorBlock, DeArrow
- v2: video and audio downloads with yt-dlp
- v3: extras

## Licence

MaterialTube is under the **Floodgate License**: view-only, all rights reserved,
until the author opens the floodgate. From then on it becomes GPL-3.0-or-later.
See [LICENSE](LICENSE).
