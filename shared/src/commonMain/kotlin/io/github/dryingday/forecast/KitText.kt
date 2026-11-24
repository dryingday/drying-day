package io.github.dryingday.forecast

import io.github.dryingday.engine.Equipment
import io.github.dryingday.shared.resources.Res
import io.github.dryingday.shared.resources.kit_airer
import io.github.dryingday.shared.resources.kit_dehumidifier
import io.github.dryingday.shared.resources.kit_heated_airer
import io.github.dryingday.shared.resources.kit_link_said
import io.github.dryingday.shared.resources.kit_link_unsaid
import io.github.dryingday.shared.resources.kit_tumble_dryer
import org.jetbrains.compose.resources.StringResource

/** The item's name on the kit screen. */
internal fun Equipment.text(): StringResource = when (this) {
    Equipment.AIRER -> Res.string.kit_airer
    Equipment.HEATED_AIRER -> Res.string.kit_heated_airer
    Equipment.DEHUMIDIFIER -> Res.string.kit_dehumidifier
    Equipment.TUMBLE_DRYER -> Res.string.kit_tumble_dryer
}

/** The last line of the Indoors section: an invitation until the kit is said, then an offer to change it. */
internal fun kitLinkText(kit: Set<Equipment>?): StringResource =
    if (kit == null) Res.string.kit_link_unsaid else Res.string.kit_link_said
