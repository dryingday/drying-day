package io.github.dryingday.forecast

import io.github.dryingday.engine.DayAdvice
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.cloth_spoken
import io.github.dryingday.shared.resources.day_short_friday
import io.github.dryingday.shared.resources.day_short_monday
import io.github.dryingday.shared.resources.day_short_saturday
import io.github.dryingday.shared.resources.day_short_sunday
import io.github.dryingday.shared.resources.day_short_thursday
import io.github.dryingday.shared.resources.day_short_tuesday
import io.github.dryingday.shared.resources.day_short_wednesday
import io.github.dryingday.shared.resources.day_today
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource

// The words on the washing line's cloths. The full name is [dayLabel]. See docs/bolts/09-washing-line.md.

/** A small cloth's name: "Today", then the weekday's three letters. */
internal fun clothShortName(today: LocalDate, index: Int): StringResource = when (index) {
    0 -> Res.string.day_today
    else -> today.plus(index, DateTimeUnit.DAY).dayOfWeek.shortText()
}

private fun DayOfWeek.shortText(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.day_short_monday
    DayOfWeek.TUESDAY -> Res.string.day_short_tuesday
    DayOfWeek.WEDNESDAY -> Res.string.day_short_wednesday
    DayOfWeek.THURSDAY -> Res.string.day_short_thursday
    DayOfWeek.FRIDAY -> Res.string.day_short_friday
    DayOfWeek.SATURDAY -> Res.string.day_short_saturday
    DayOfWeek.SUNDAY -> Res.string.day_short_sunday
}

/**
 * What a screen reader says for the cloth at [index]: the full name and the verdict, "Tomorrow, Finish indoors";
 * the name alone for a null day. [resolve] turns a message into text.
 */
internal fun clothSpoken(today: LocalDate, index: Int, advice: DayAdvice?, resolve: (Message) -> String): String {
    val name = resolve(Message(dayLabel(today, index)))
    if (advice == null) return name
    return resolve(Message(Res.string.cloth_spoken, listOf(name, resolve(Message(advice.headline())))))
}
