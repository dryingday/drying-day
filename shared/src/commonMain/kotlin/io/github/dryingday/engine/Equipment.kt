package io.github.dryingday.engine

/**
 * What someone dries with at home, in the order the screen and the stored value use. The indoor plan follows it
 * (docs/bolts/10-equipment.md); the verdict, the times and the tips never do. A radiator isn't here: it's assumed.
 */
enum class Equipment { AIRER, HEATED_AIRER, DEHUMIDIFIER, TUMBLE_DRYER }
