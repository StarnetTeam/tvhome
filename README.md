# TV Home

A small, self-contained Android TV / Android box launcher. One app, no other project needed.

- **Home screen** like the interactive preview: wallpaper, greeting and clock, glass buttons with icons
  (Inputs, Wi-Fi, Settings), a strip of live cards, the dock and a grid of all your apps. D-pad only,
  with the grow effect on the focused item.
- **Ten themes** with real wallpapers: default, aurora, dussehra, glass, nature-calm, retro, ruska, scenes,
  six-columns, sweden. Wallpapers rotate on their own (every 5 or 60 minutes) or follow the time of day (retro).
- **Live info cards**, all with real data: weather (Open-Meteo), prayer times with a countdown (Aladhan),
  today's matches (BBC Sport page, unofficial), world clock, memory and storage, network, auto-standby
  countdown, remote-access (network debugging) status. Opening a card shows the full screen.
- **Hold OK on an app** for its menu: move, clear data, uninstall, hide.
- **Settings on the TV** (categories on one side, options on the other): Themes, Add-ons (cards, city,
  prayer method), Layout (language, info cards, apps per row, wallpaper, reduce animations, tile menu),
  App library, Safety, Power (auto standby 1 / 4 / 6 hours, wait while audio plays, accessibility service).
- **Arabic** with full right-to-left, switchable in Settings.
- **Reduce animations**: on automatically for devices with about 2 GB of RAM or less. It removes the
  transitions but keeps the instant enlargement of the focused item.
- Java only and no libraries.

Not included yet: the preview's **Cleanup** and **Restore** options. They need a local ADB engine, a separate piece of work.

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

Themes and wallpapers come from the MIT-licensed cleanyourtv project; see `NOTICE.md` and `WALLPAPER_CREDITS.md`.

## License

MIT, see `LICENSE`.
