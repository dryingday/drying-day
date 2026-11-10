package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.engine.IndoorStep
import io.github.dryingday.engine.Reason
import io.github.dryingday.engine.Tip
import io.github.dryingday.engine.Verdict
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.day_today
import io.github.dryingday.shared.resources.day_tomorrow
import io.github.dryingday.shared.resources.forecast_error_bad_response
import io.github.dryingday.shared.resources.forecast_error_service
import io.github.dryingday.shared.resources.forecast_error_unreachable
import io.github.dryingday.shared.resources.forecast_stale_error_bad_response
import io.github.dryingday.shared.resources.forecast_stale_error_service
import io.github.dryingday.shared.resources.forecast_stale_error_unreachable
import io.github.dryingday.shared.resources.indoor_airer_spaced
import io.github.dryingday.shared.resources.indoor_dehumidifier_or_closed_room
import io.github.dryingday.shared.resources.indoor_not_on_radiator
import io.github.dryingday.shared.resources.indoor_open_window
import io.github.dryingday.shared.resources.indoor_turn_heavy_items
import io.github.dryingday.shared.resources.reason_cold
import io.github.dryingday.shared.resources.reason_damp_air
import io.github.dryingday.shared.resources.reason_day_over
import io.github.dryingday.shared.resources.reason_fog
import io.github.dryingday.shared.resources.reason_rain
import io.github.dryingday.shared.resources.reason_short_daylight
import io.github.dryingday.shared.resources.reason_still_air
import io.github.dryingday.shared.resources.tip_darks_inside_out
import io.github.dryingday.shared.resources.tip_heavy_items
import io.github.dryingday.shared.resources.tip_peg_well
import io.github.dryingday.shared.resources.tip_showers_possible
import io.github.dryingday.shared.resources.tip_space_out
import io.github.dryingday.shared.resources.verdict_day_over
import io.github.dryingday.shared.resources.verdict_dont_bother
import io.github.dryingday.shared.resources.verdict_partly_dry
import io.github.dryingday.shared.resources.verdict_short_dont_bother
import io.github.dryingday.shared.resources.verdict_short_none
import io.github.dryingday.shared.resources.verdict_short_partly_dry
import io.github.dryingday.shared.resources.verdict_short_will_dry
import io.github.dryingday.shared.resources.verdict_will_dry
import io.github.dryingday.shared.resources.weekday_friday
import io.github.dryingday.shared.resources.weekday_monday
import io.github.dryingday.shared.resources.weekday_saturday
import io.github.dryingday.shared.resources.weekday_sunday
import io.github.dryingday.shared.resources.weekday_thursday
import io.github.dryingday.shared.resources.weekday_tuesday
import io.github.dryingday.shared.resources.weekday_wednesday
import io.github.dryingday.weather.SourceError
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource

// The words for the engine's codes. Each is an exhaustive `when`, so a new code doesn't compile without text.
// See docs/bolts/05-main-screen.md, "Text" and "Times".

internal fun Reason.text(): StringResource = when (this) {
    Reason.RAIN -> Res.string.reason_rain
    Reason.FOG -> Res.string.reason_fog
    Reason.COLD -> Res.string.reason_cold
    Reason.DAMP_AIR -> Res.string.reason_damp_air
    Reason.STILL_AIR -> Res.string.reason_still_air
    Reason.SHORT_DAYLIGHT -> Res.string.reason_short_daylight
    Reason.DAY_OVER -> Res.string.reason_day_over
}

internal fun Tip.text(): StringResource = when (this) {
    Tip.PEG_WELL -> Res.string.tip_peg_well
    Tip.SHOWERS_POSSIBLE -> Res.string.tip_showers_possible
    Tip.HEAVY_ITEMS -> Res.string.tip_heavy_items
    Tip.SPACE_OUT -> Res.string.tip_space_out
    Tip.DARKS_INSIDE_OUT -> Res.string.tip_darks_inside_out
}

internal fun IndoorStep.text(): StringResource = when (this) {
    IndoorStep.AIRER_SPACED -> Res.string.indoor_airer_spaced
    IndoorStep.OPEN_WINDOW -> Res.string.indoor_open_window
    IndoorStep.DEHUMIDIFIER_OR_CLOSED_ROOM -> Res.string.indoor_dehumidifier_or_closed_room
    IndoorStep.NOT_ON_RADIATOR -> Res.string.indoor_not_on_radiator
    IndoorStep.TURN_HEAVY_ITEMS -> Res.string.indoor_turn_heavy_items
}

/** The message when there is no forecast to show. */
internal fun SourceError.textWithoutForecast(): StringResource = when (this) {
    SourceError.UNREACHABLE -> Res.string.forecast_error_unreachable
    SourceError.SERVICE -> Res.string.forecast_error_service
    SourceError.BAD_RESPONSE -> Res.string.forecast_error_bad_response
}

/** The message under a forecast that's shown. */
internal fun SourceError.textUnderForecast(): StringResource = when (this) {
    SourceError.UNREACHABLE -> Res.string.forecast_stale_error_unreachable
    SourceError.SERVICE -> Res.string.forecast_stale_error_service
    SourceError.BAD_RESPONSE -> Res.string.forecast_stale_error_bad_response
}

internal fun DayOfWeek.text(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.weekday_monday
    DayOfWeek.TUESDAY -> Res.string.weekday_tuesday
    DayOfWeek.WEDNESDAY -> Res.string.weekday_wednesday
    DayOfWeek.THURSDAY -> Res.string.weekday_thursday
    DayOfWeek.FRIDAY -> Res.string.weekday_friday
    DayOfWeek.SATURDAY -> Res.string.weekday_saturday
    DayOfWeek.SUNDAY -> Res.string.weekday_sunday
}

/**
 * The headline for a day: the verdict, or, when the day's only reason is that its daylight is over, that it's
 * too late.
 */
internal fun DayAdvice.headline(): StringResource = when {
    verdict == Verdict.DONT_BOTHER && reasons == listOf(Reason.DAY_OVER) -> Res.string.verdict_day_over
    verdict == Verdict.WILL_DRY -> Res.string.verdict_will_dry
    verdict == Verdict.PARTLY_DRY -> Res.string.verdict_partly_dry
    else -> Res.string.verdict_dont_bother
}

/** What the day tabs say: the verdict in short, or a dash for a day with too little forecast. */
internal fun DayAdvice?.shortVerdict(): StringResource = when (this?.verdict) {
    Verdict.WILL_DRY -> Res.string.verdict_short_will_dry
    Verdict.PARTLY_DRY -> Res.string.verdict_short_partly_dry
    Verdict.DONT_BOTHER -> Res.string.verdict_short_dont_bother
    null -> Res.string.verdict_short_none
}

/** The label of the tab at [index] (0 to 2) for the day [today] is: Today, Tomorrow, then the weekday. */
internal fun dayLabel(today: LocalDate, index: Int): StringResource = when (index) {
    0 -> Res.string.day_today
    1 -> Res.string.day_tomorrow
    else -> today.plus(index, DateTimeUnit.DAY).dayOfWeek.text()
}
