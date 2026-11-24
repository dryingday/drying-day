package io.github.dryingday.engine

import io.github.dryingday.engine.DryingThresholds.INDOOR_TIME_AT_LEAST
import io.github.dryingday.engine.DryingThresholds.MAX_TIPS
import io.github.dryingday.engine.DryingThresholds.OPEN_WINDOW_BELOW_RH_PERCENT
import io.github.dryingday.engine.DryingThresholds.PEG_WELL_GUST_KMH
import io.github.dryingday.engine.DryingThresholds.SHOWERS_FROM_PERCENT
import io.github.dryingday.engine.DryingThresholds.STILL_AIR_BELOW_FACTOR
import io.github.dryingday.engine.DryingThresholds.STRONG_SUN_FOR
import io.github.dryingday.engine.DryingThresholds.STRONG_SUN_WM2
import io.github.dryingday.engine.DryingThresholds.WET_PRECIPITATION_PROBABILITY_PERCENT
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Hanging tips for a day the washing goes out, judged over its time on the line, from [window]'s hang-out to
 * its bring-in. At most three, in the order of [Tip].
 */
internal fun tips(hours: List<HourlyWeather>, window: Window, load: Load): List<Tip> {
    val onTheLine = Interval(window.hangOut, window.bringIn)
    fun any(condition: (HourlyWeather) -> Boolean) = hours.timeIn(onTheLine, condition) > Duration.ZERO
    val applies = mapOf(
        Tip.PEG_WELL to any { it.windGustKmh >= PEG_WELL_GUST_KMH },
        Tip.SHOWERS_POSSIBLE to any {
            (it.precipitationProbabilityPercent ?: 0.0) in SHOWERS_FROM_PERCENT..<WET_PRECIPITATION_PROBABILITY_PERCENT
        },
        Tip.HEAVY_ITEMS to (load == Load.HEAVY),
        Tip.SPACE_OUT to hours.mostOf(onTheLine) { windFactor(it.windSpeedKmh) < STILL_AIR_BELOW_FACTOR },
        Tip.DARKS_INSIDE_OUT to
            (hours.timeIn(onTheLine) { it.shortwaveRadiationWm2 >= STRONG_SUN_WM2 } >= STRONG_SUN_FOR),
    )
    return Tip.entries.filter { applies.getValue(it) }.take(MAX_TIPS)
}

/**
 * Steps for drying indoors after bring-in for a partly dry load, or over the day's usable time when it isn't
 * worth hanging out ([indoorTime]). Whether to let outdoor air in depends on its mean humidity, weighted by
 * time, from the indoor time's start (never before [now]) for at least six hours. With any of that time
 * missing from the forecast, none of the humidity steps is given: never a guess. [kit] is what's dried with,
 * or null when not said (docs/bolts/10-equipment.md).
 */
internal fun indoorPlan(
    hours: List<HourlyWeather>,
    indoorTime: Interval,
    now: Instant,
    load: Load,
    kit: Set<Equipment>? = null,
): List<IndoorStep> {
    val start = maxOf(indoorTime.start, now)
    val span = Interval(start, maxOf(indoorTime.end, start + INDOOR_TIME_AT_LEAST))
    val outdoorAirIsDry = hours.meanHumidityIn(span)?.let { it < OPEN_WINDOW_BELOW_RH_PERCENT }
    return listOfNotNull(
        airerStep(kit),
        humidityStep(kit, outdoorAirIsDry),
        IndoorStep.NOT_ON_RADIATOR,
        IndoorStep.TURN_HEAVY_ITEMS.takeIf { load == Load.HEAVY },
        IndoorStep.TUMBLE_TO_FINISH.takeIf { kit != null && Equipment.TUMBLE_DRYER in kit },
    )
}

/** What to dry on: the heated airer wins over a plain one, and a kit with neither airer gets hangers. */
private fun airerStep(kit: Set<Equipment>?): IndoorStep = when {
    kit == null -> IndoorStep.AIRER_SPACED
    Equipment.HEATED_AIRER in kit -> IndoorStep.HEATED_AIRER_SPACED
    Equipment.AIRER in kit -> IndoorStep.AIRER_SPACED
    else -> IndoorStep.HANGERS_SPACED
}

/** The air step: none when the outdoor humidity is unknown, and no dehumidifier mention on a dry day. */
private fun humidityStep(kit: Set<Equipment>?, outdoorAirIsDry: Boolean?): IndoorStep? = when {
    outdoorAirIsDry == null -> null
    outdoorAirIsDry -> IndoorStep.OPEN_WINDOW
    kit == null -> IndoorStep.DEHUMIDIFIER_OR_CLOSED_ROOM
    Equipment.DEHUMIDIFIER in kit -> IndoorStep.DEHUMIDIFIER_CLOSED_ROOM
    else -> IndoorStep.WARM_ROOM_AIRED
}

/** Mean relative humidity over [span], weighted by time; null unless the hours cover all of it. */
private fun List<HourlyWeather>.meanHumidityIn(span: Interval): Double? {
    val covered = timeIn(span) { true }
    return if (covered < span.length) {
        null
    } else {
        sumOf { it.relativeHumidityPercent * (it.usableTime(span, span.start) / covered) }
    }
}
