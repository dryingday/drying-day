package io.github.dryingday.engine

import io.github.dryingday.engine.DryingThresholds.INDOOR_FALLBACK
import io.github.dryingday.engine.DryingThresholds.OPEN_WINDOW_BELOW_RH_PERCENT
import io.github.dryingday.engine.DryingThresholds.PEG_WELL_GUST_KMH
import io.github.dryingday.engine.DryingThresholds.SHOWERS_FROM_PERCENT
import io.github.dryingday.engine.DryingThresholds.STILL_AIR_BELOW_FACTOR
import io.github.dryingday.engine.DryingThresholds.STRONG_SUN_FOR
import io.github.dryingday.engine.DryingThresholds.STRONG_SUN_WM2
import io.github.dryingday.engine.DryingThresholds.WET_PRECIPITATION_PROBABILITY_PERCENT
import kotlin.time.Duration

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
        Tip.SPACE_OUT to
            (hours.timeIn(onTheLine) { windFactor(it.windSpeedKmh) < STILL_AIR_BELOW_FACTOR } * 2 > onTheLine.length),
        Tip.DARKS_INSIDE_OUT to
            (hours.timeIn(onTheLine) { it.shortwaveRadiationWm2 >= STRONG_SUN_WM2 } >= STRONG_SUN_FOR),
    )
    return Tip.entries.filter { applies.getValue(it) }.take(MAX_TIPS)
}

/**
 * Steps for drying indoors over [indoorTime]: after bring-in for a partly dry load, or the day's usable time
 * when it isn't worth hanging out. When that's empty, the six hours from its start. Whether to let outdoor
 * air in depends on its mean humidity then; with no forecast for that time, neither step is given.
 */
internal fun indoorPlan(hours: List<HourlyWeather>, indoorTime: Interval, load: Load): List<IndoorStep> {
    val span = indoorTime.takeIf { it.length > Duration.ZERO }
        ?: Interval(indoorTime.start, indoorTime.start + INDOOR_FALLBACK)
    val outdoorAirIsDry = hours.meanHumidityIn(span)?.let { it < OPEN_WINDOW_BELOW_RH_PERCENT }
    return IndoorStep.entries.filter {
        when (it) {
            IndoorStep.AIRER_SPACED, IndoorStep.NOT_ON_RADIATOR -> true
            IndoorStep.OPEN_WINDOW -> outdoorAirIsDry == true
            IndoorStep.DEHUMIDIFIER_OR_CLOSED_ROOM -> outdoorAirIsDry == false
            IndoorStep.TURN_HEAVY_ITEMS -> load == Load.HEAVY
        }
    }
}

/** Mean relative humidity over [span], weighted by time; null when no hour covers any of it. */
private fun List<HourlyWeather>.meanHumidityIn(span: Interval): Double? {
    val weights = map { it to it.usableTime(span, span.start) }.filter { (_, time) -> time > Duration.ZERO }
    val total = weights.fold(Duration.ZERO) { sum, (_, time) -> sum + time }
    return if (total >
        Duration.ZERO
    ) {
        weights.sumOf { (hour, time) -> hour.relativeHumidityPercent * (time / total) }
    } else {
        null
    }
}

private const val MAX_TIPS = 3
