# Drying Day

Will my washing dry on the line today?

Drying Day reads the forecast for your drying window and gives a clear verdict (**Will dry**, **Partly dry, finish indoors** or **Don't bother, dry indoors**), the best times to hang out and bring in, and tips for the conditions.

Free and open source, no account, no tracking. Android first, built with Kotlin Multiplatform.

Status: early development. See [docs/PLAN.md](docs/PLAN.md).

Weather data by [Open-Meteo](https://open-meteo.com/) (CC BY 4.0).

## Building

With JDK 21 and the Android SDK:

```
./gradlew check assembleDebug
```

The APK is in `androidApp/build/outputs/apk/debug/`. [CONTRIBUTING.md](CONTRIBUTING.md) has the
details and the rules for changes.

## Licence

GPL-3.0-or-later. See [LICENSE](LICENSE).
