# TV Home

A small, self-contained Android TV / Android box launcher. One app, no other project needed.

- **Home screen** with a clock, a dock and a grid of all your apps, built for the remote (D-pad only).
- **Live info cards** above the dock. Every card shows real data, not placeholder text:
  weather (Open-Meteo), prayer times with a live countdown (Aladhan), today's matches (BBC Sport page,
  unofficial), world clock, memory and storage, network, and the auto-standby countdown.
- **Settings on the TV itself**: language (System / English / Arabic with full right-to-left),
  six themes, reduce motion (automatic on devices with about 2 GB of RAM or less), cards on or off,
  city, prayer calculation method, auto standby (off / 1 h / 4 h / 6 h), dock and hidden apps.
- **Auto standby**: after the chosen number of hours with no remote key press, the TV is put to sleep
  (Android 9+). Optionally it waits while audio or video is playing. Needs the accessibility service
  switched on once.
- Java only and no libraries, so the APK stays small.

## Build

Push to GitHub and run the **Build APK** workflow (`build-apk.yml`, copy it to
`.github/workflows/build-apk.yml`). The APK is published on the `latest-build` release.
Locally: `./gradlew assembleRelease` (JDK 17, Android SDK 34).

## Data sources and terms

| Card | Source | Notes |
|---|---|---|
| Weather | Open-Meteo.com | Free, no key, **non-commercial use**, CC BY 4.0: keep the attribution. |
| Prayer times | Aladhan API | Free, no key. |
| Matches | BBC Sport scores page | **Unofficial.** BBC's terms do not allow using its content without permission. Personal use only, and the page can change at any time. Replace `BbcFootballProvider` with an official API if you distribute the app. |
| City search | Open-Meteo geocoding | Same terms as the weather. |

## License

MIT, see `LICENSE`.
