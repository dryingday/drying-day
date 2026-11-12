# Privacy policy

Last changed 2026-11-12. This file changes only by pull request, so its history is in the repository.

Drying Day has no account, no analytics, no ads and no crash reporting. The app's developer receives nothing from your phone.

## What leaves your device

The app talks to one service, [Open-Meteo](https://open-meteo.com/), over HTTPS. It makes two kinds of request:

- **The forecast**, to `api.open-meteo.com`: the coordinates of your place, rounded to two decimals (about a kilometre).
- **A place search**, to `geocoding-api.open-meteo.com`: the text you typed and your phone's language.

Open-Meteo sees your phone's IP address, as any server you connect to does, and handles it under [its own terms and privacy policy](https://open-meteo.com/en/terms). The app sends no identifier of its own. Its user agent is the HTTP library's generic one, not Android's, which would name your phone's model. Whatever your phone's networking adds itself is outside the app's control.

## When

- A search is sent only when you submit it.
- The forecast is fetched at most about once an hour while the app is open, plus when you tap Refresh. Never in the background: the app makes no requests when it isn't on screen.

## Location

The app asks for approximate location only, never precise or background location, and only when you tap "Use my approximate location". It reads one fix, saves it as a place (rounded to two decimals) and doesn't follow you. Your phone's own location service, which may be run by its maker or Google, is outside the app.

## What is kept on your device

The places you saved, their last forecasts and your choice of light or heavy load. On Android this is private to the app and excluded from backup and device transfer. Uninstalling the app removes it.

## Where you got the app

F-Droid, GitHub or wherever else you installed it from may have its own policy.

## Contact

Open an issue at <https://github.com/dryingday/drying-day/issues>.
