package io.github.dryingday.engine

import io.github.dryingday.engine.DryingThresholds.DAMP_RELATIVE_HUMIDITY_PERCENT
import io.github.dryingday.engine.DryingThresholds.FOG_VISIBILITY_M
import io.github.dryingday.engine.DryingThresholds.MAGNUS_B
import io.github.dryingday.engine.DryingThresholds.MAGNUS_C_CELSIUS
import io.github.dryingday.engine.DryingThresholds.MAGNUS_KPA
import io.github.dryingday.engine.DryingThresholds.SUN_BONUS
import io.github.dryingday.engine.DryingThresholds.VPD_POINTS
import io.github.dryingday.engine.DryingThresholds.WET_PRECIPITATION_MM
import io.github.dryingday.engine.DryingThresholds.WET_PRECIPITATION_PROBABILITY_PERCENT
import io.github.dryingday.engine.DryingThresholds.WIND_FACTOR
import kotlin.math.exp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

internal fun HourlyWeather.kind(): HourKind = when {
    precipitationMm >= WET_PRECIPITATION_MM -> HourKind.WET
    (precipitationProbabilityPercent ?: 0.0) >= WET_PRECIPITATION_PROBABILITY_PERCENT -> HourKind.WET
    visibilityM != null && visibilityM < FOG_VISIBILITY_M -> HourKind.FOG
    relativeHumidityPercent >= DAMP_RELATIVE_HUMIDITY_PERCENT -> HourKind.DEW
    else -> HourKind.DRYING
}

/** Points this hour would earn in a full hour of daylight: 0 unless it's [HourKind.DRYING]. */
internal fun HourlyWeather.pointsPerHour(): Double {
    if (kind() != HourKind.DRYING) return 0.0
    val vpd = vpdPoints(vapourPressureDeficitKpa(temperatureC, relativeHumidityPercent))
    return vpd * windFactor(windSpeedKmh) + sunBonus(shortwaveRadiationWm2, vpd)
}

/** Points earned over [usable] of this hour: pro rata for a partial hour. */
internal fun HourlyWeather.points(usable: Duration): Double = pointsPerHour() * (usable / 1.hours)

/** e_s(T) × (1 − RH/100), with the Magnus formula for saturation vapour pressure e_s, in kPa. */
internal fun vapourPressureDeficitKpa(temperatureC: Double, relativeHumidityPercent: Double): Double {
    val saturation = MAGNUS_KPA * exp(MAGNUS_B * temperatureC / (temperatureC + MAGNUS_C_CELSIUS))
    return saturation * (1 - relativeHumidityPercent / PERCENT)
}

internal fun vpdPoints(vpdKpa: Double): Double = VPD_POINTS.lookup(vpdKpa)

internal fun windFactor(windSpeedKmh: Double): Double = WIND_FACTOR.lookup(windSpeedKmh)

/** Sun doesn't dry washing in saturated air, so there's no bonus without humidity-and-temperature points. */
internal fun sunBonus(shortwaveRadiationWm2: Double, vpdPoints: Double): Double =
    if (vpdPoints > 0.0) SUN_BONUS.lookup(shortwaveRadiationWm2) else 0.0

private const val PERCENT = 100.0
