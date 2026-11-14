package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format.char
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Times as the screen shows them. See docs/bolts/05-main-screen.md, "Times".

/** Formats an instant as a clock time in a zone, in the device's 12- or 24-hour format. */
typealias TimeFormat = (Instant, TimeZone) -> String

private val twentyFourHour = LocalTime.Format {
    hour()
    char(':')
    minute()
}

/** `HH:mm`, for the iOS wrapper and tests. Android passes one built on the device's setting. */
val twentyFourHourFormat: TimeFormat = { instant, zone -> twentyFourHour.format(instant.toLocalDateTime(zone).time) }

/** The two times to show for a day with a window, to the minute. */
class Window(val hangOut: Instant, val bringIn: Instant)

/**
 * The hang-out's seconds rounded up and the bring-in's down, so the times never fall before sunrise or `now`, or
 * after rain or sunset. If the two would meet in a minute, both are the hang-out's minute rounded down. Null for a
 * day with no times.
 */
internal fun DayAdvice.window(): Window? {
    val start = hangOut
    val end = bringIn
    if (start == null || end == null) return null
    val up = start.roundedUp()
    val down = end.roundedDown()
    val meet = down <= up
    return Window(if (meet) start.roundedDown() else up, if (meet) start.roundedDown() else down)
}

private fun Instant.roundedDown() = Instant.fromEpochSeconds(epochSeconds - epochSeconds.mod(SECONDS_PER_MINUTE))

private fun Instant.roundedUp(): Instant {
    val down = roundedDown()
    return if (down == this) down else down + 1.minutes
}

private const val SECONDS_PER_MINUTE = 60L

/** "Times are local to the place" shows when the device's UTC offset at [now] differs from the forecast zone's. */
internal fun timesAreLocalToPlace(forecastZone: TimeZone, deviceZone: TimeZone, now: Instant): Boolean =
    forecastZone.offsetAt(now) != deviceZone.offsetAt(now)

/** Which day "Updated" names. [UNKNOWN] when the fetch time is after now, so there is no age to name. */
enum class UpdatedDay { TODAY, YESTERDAY, WEEKDAY, UNKNOWN }

/** How "Updated" reads: the day, the time and the zone the time is in. [weekday] is set for [UpdatedDay.WEEKDAY]. */
class UpdatedLabel(val day: UpdatedDay, val weekday: DayOfWeek?, val time: String, val zone: String)

/** The days, time and zone are the place's, as every other time on the screen is. */
internal fun updatedLabel(fetchedAt: Instant, now: Instant, zone: TimeZone, formatTime: TimeFormat): UpdatedLabel {
    val fetched = fetchedAt.toLocalDateTime(zone).date
    val daysAgo = fetched.daysUntil(now.toLocalDateTime(zone).date)
    val day = when {
        fetchedAt > now -> UpdatedDay.UNKNOWN
        daysAgo <= 0 -> UpdatedDay.TODAY
        daysAgo == 1 -> UpdatedDay.YESTERDAY
        else -> UpdatedDay.WEEKDAY
    }
    val weekday = if (day == UpdatedDay.WEEKDAY) fetched.dayOfWeek else null
    return UpdatedLabel(day, weekday, formatTime(fetchedAt, zone), offsetText(zone.offsetAt(fetchedAt)))
}

/** `UTC`, `UTC+1`, `UTC-5`, `UTC+5:45`: a zone's abbreviation isn't known to common code, and an offset is. */
internal fun offsetText(offset: UtcOffset): String {
    val total = offset.totalSeconds
    if (total == 0) return "UTC"
    val minutes = abs(total) / SECONDS_PER_MINUTE.toInt()
    val sign = if (total < 0) "-" else "+"
    val rest = minutes % MINUTES_PER_HOUR
    return "UTC$sign${minutes / MINUTES_PER_HOUR}" + if (rest == 0) "" else ":" + rest.toString().padStart(2, '0')
}

private const val MINUTES_PER_HOUR = 60

/** `70 %`, for the dryness line. */
internal fun DayAdvice.drynessText() = "$dryness %"
