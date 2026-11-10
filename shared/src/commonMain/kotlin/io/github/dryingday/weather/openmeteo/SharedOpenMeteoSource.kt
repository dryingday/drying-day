package io.github.dryingday.weather.openmeteo

import io.github.dryingday.weather.platformHttpEngine

/** The app's one [OpenMeteoSource]. It outlives every screen, so its client is built once, on first use. */
val sharedOpenMeteoSource: OpenMeteoSource by lazy { OpenMeteoSource(platformHttpEngine()) }
