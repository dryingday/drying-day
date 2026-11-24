package io.github.dryingday.forecast

import com.russhwolf.settings.Settings
import io.github.dryingday.engine.Equipment

internal const val KIT_KEY = "equipment"

/**
 * What the person dries with, from [settings]. No key is null ("not said"); an empty string is the empty set. Unknown
 * codes are dropped, so a value of only unknown codes is the empty set too. See docs/bolts/10-equipment.md, "Stored".
 */
internal fun readKit(settings: Settings): Set<Equipment>? {
    val stored = settings.getStringOrNull(KIT_KEY) ?: return null
    val codes = stored.split(',').toSet()
    return Equipment.entries.filterTo(mutableSetOf()) { it.name in codes }
}

/** Stores [kit] as its codes, comma-separated in the enum's order. The empty set is the empty string. */
internal fun writeKit(settings: Settings, kit: Set<Equipment>) {
    settings.putString(KIT_KEY, Equipment.entries.filter { it in kit }.joinToString(",") { it.name })
}
