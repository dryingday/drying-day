package io.github.dryingday.forecast

import io.github.dryingday.engine.Condition
import io.github.dryingday.engine.Load
import io.github.dryingday.engine.Verdict
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.condition_clear
import io.github.dryingday.shared.resources.condition_cloudy
import io.github.dryingday.shared.resources.condition_drizzle
import io.github.dryingday.shared.resources.condition_fog
import io.github.dryingday.shared.resources.condition_partly_cloudy
import io.github.dryingday.shared.resources.condition_rain
import io.github.dryingday.shared.resources.condition_showers
import io.github.dryingday.shared.resources.condition_snow
import io.github.dryingday.shared.resources.condition_thunder
import io.github.dryingday.shared.resources.load_heavy
import io.github.dryingday.shared.resources.load_light
import io.github.dryingday.shared.resources.sky_clear
import io.github.dryingday.shared.resources.sky_cloudy
import io.github.dryingday.shared.resources.sky_drizzle
import io.github.dryingday.shared.resources.sky_fog
import io.github.dryingday.shared.resources.sky_partly_cloudy
import io.github.dryingday.shared.resources.sky_rain
import io.github.dryingday.shared.resources.sky_showers
import io.github.dryingday.shared.resources.sky_snow
import io.github.dryingday.shared.resources.sky_thunder
import io.github.dryingday.shared.resources.verdict_dont_bother
import io.github.dryingday.shared.resources.verdict_partly_dry
import io.github.dryingday.shared.resources.verdict_will_dry
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

// The pictures for the sky and the verdicts, drawn for this app (sources in art/), and the sky's spoken names.
// Each is an exhaustive `when`, so a new code doesn't compile without them. A verdict picture has no spoken
// name: the written verdict beside it is what's read. See docs/bolts/07-weather-at-a-glance.md, "The pictures".
// The loads' weights are bolt 8's: docs/bolts/08-fewer-words.md, "The pictures".

internal fun Condition.picture(): DrawableResource = when (this) {
    Condition.CLEAR -> Res.drawable.condition_clear
    Condition.PARTLY_CLOUDY -> Res.drawable.condition_partly_cloudy
    Condition.CLOUDY -> Res.drawable.condition_cloudy
    Condition.FOG -> Res.drawable.condition_fog
    Condition.DRIZZLE -> Res.drawable.condition_drizzle
    Condition.RAIN -> Res.drawable.condition_rain
    Condition.SHOWERS -> Res.drawable.condition_showers
    Condition.SNOW -> Res.drawable.condition_snow
    Condition.THUNDER -> Res.drawable.condition_thunder
}

internal fun Condition.spokenName(): StringResource = when (this) {
    Condition.CLEAR -> Res.string.sky_clear
    Condition.PARTLY_CLOUDY -> Res.string.sky_partly_cloudy
    Condition.CLOUDY -> Res.string.sky_cloudy
    Condition.FOG -> Res.string.sky_fog
    Condition.DRIZZLE -> Res.string.sky_drizzle
    Condition.RAIN -> Res.string.sky_rain
    Condition.SHOWERS -> Res.string.sky_showers
    Condition.SNOW -> Res.string.sky_snow
    Condition.THUNDER -> Res.string.sky_thunder
}

internal fun Verdict.picture(): DrawableResource = when (this) {
    Verdict.WILL_DRY -> Res.drawable.verdict_will_dry
    Verdict.PARTLY_DRY -> Res.drawable.verdict_partly_dry
    Verdict.DONT_BOTHER -> Res.drawable.verdict_dont_bother
}

// The light weight and the heavy one: the same shape at two sizes. Decorative, as the toggle's half is labelled.
internal fun Load.picture(): DrawableResource = when (this) {
    Load.LIGHT -> Res.drawable.load_light
    Load.HEAVY -> Res.drawable.load_heavy
}
