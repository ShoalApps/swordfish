# Tintloom Video

A Material 3 Expressive client for watching YouTube videos on Android, part of the **Tintloom** project:
open-source apps that take on your wallpaper colours with Material You.

- Jetpack Compose UI with dynamic colour, light/dark themes and spring animations
- NewPipeExtractor backend: no Google account, no ads
- Media3 ExoPlayer with background play and picture-in-picture
- SponsorBlock, DeArrow and Return YouTube Dislike built in

## Getting the APK

Every push to `main` builds a new APK with GitHub Actions.
Open the **Releases** page on your phone and install `TintloomVideo.apk` from the newest build.

Every build is also run on an Android emulator; screenshots and logs land on the `ci-shots` branch.

## Staying up to date

- The app checks GitHub once a day and offers new releases; Android asks before installing.
- Dependabot proposes library updates weekly; each is built and tested and only merged if it passes.
- Every day a workflow follows the NewPipeExtractor version the NewPipe app ships, so YouTube fixes
  arrive without anyone building anything by hand.

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

Tintloom Video is free software under the **GNU General Public License v3.0 or later**
(the same licence as NewPipeExtractor, which it is built on). You may use, study, share
and change it, as long as anything you distribute that is based on it stays under the
same licence with its source available. See [LICENSE](LICENSE).

Tintloom Video is not affiliated with, endorsed by or sponsored by Google or YouTube.
