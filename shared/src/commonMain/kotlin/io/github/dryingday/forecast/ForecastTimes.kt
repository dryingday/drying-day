package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
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

/** How "Updated" reads: with a day when the fetch was on an earlier local date, plain for an unknown age. */
class UpdatedLabel(val earlierDate: Boolean, val time: String)

internal fun updatedLabel(fetchedAt: Instant, now: Instant, zone: TimeZone, formatTime: TimeFormat): UpdatedLabel {
    val unknownAge = fetchedAt > now
    val earlier = !unknownAge && fetchedAt.toLocalDateTime(zone).date < now.toLocalDateTime(zone).date
    return UpdatedLabel(earlier, formatTime(fetchedAt, zone))
}

/** `70 %`, for the dryness line. */
internal fun DayAdvice.drynessText() = "$dryness %"
