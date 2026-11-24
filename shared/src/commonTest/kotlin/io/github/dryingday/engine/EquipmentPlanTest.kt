package io.github.dryingday.engine

import io.github.dryingday.reference.allReferenceCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The indoor plan by kit (docs/bolts/10-equipment.md, "The indoor plan"). Days 06:00–20:00, asked at 08:00. */
class EquipmentPlanTest {
    private val heatedAirer = IndoorStep.HEATED_AIRER_SPACED
    private val airer = IndoorStep.AIRER_SPACED
    private val hangers = IndoorStep.HANGERS_SPACED
    private val window = IndoorStep.OPEN_WINDOW
    private val dehumidifierOrRoom = IndoorStep.DEHUMIDIFIER_OR_CLOSED_ROOM
    private val dehumidifierRoom = IndoorStep.DEHUMIDIFIER_CLOSED_ROOM
    private val warmRoom = IndoorStep.WARM_ROOM_AIRED
    private val radiator = IndoorStep.NOT_ON_RADIATOR
    private val turn = IndoorStep.TURN_HEAVY_ITEMS
    private val tumble = IndoorStep.TUMBLE_TO_FINISH

    private val humidSteps = setOf(window, dehumidifierOrRoom, dehumidifierRoom, warmRoom)

    private val kits: List<Set<Equipment>> = (0..<(1 shl Equipment.entries.size)).map { bits ->
        Equipment.entries.filterIndexed { i, _ -> (bits shr i) % 2 == 1 }.toSet()
    }

    private fun kit(vararg items: Equipment) = items.toSet()

    /** Sunny 08–11, then rain at [humidity] %: a heavy load comes in partly dry at 11:00. */
    private fun rainFrom11(humidity: Double): Forecast {
        val wet = W(relativeHumidityPercent = humidity, precipitationMm = 2.0)
        return forecast { _, h -> if (h < 11) sunny else wet }
    }

    private val humid = rainFrom11(90.0)
    private val dry = rainFrom11(50.0)

    /** Partly dry, in at 16:00, with the six hours after missing. */
    private val noIndoorHours = forecast(
        sunrise = 8 to 0,
        sunset = 16 to 0,
        skipHours = (16..21).map { at(day0, it to 0) }.toSet(),
    ) { _, _ -> grey }

    private fun plan(f: Forecast, kit: Set<Equipment>?, load: Load = Load.HEAVY, now: Instant = at(day0, 8 to 0)) =
        assertNotNull(advise(f, load, now, kit).first()).indoorPlan

    @Test
    fun aHeatedAirerWinsOverAPlainOne() {
        assertEquals(heatedAirer, plan(dry, kit(Equipment.HEATED_AIRER)).first())
        assertEquals(heatedAirer, plan(dry, kit(Equipment.HEATED_AIRER, Equipment.AIRER)).first())
    }

    @Test
    fun aPlainAirerAlone() {
        assertEquals(listOf(airer, window, radiator, turn), plan(dry, kit(Equipment.AIRER)))
    }

    @Test
    fun neitherAirerGivesHangers() {
        assertEquals(hangers, plan(dry, kit()).first())
        assertEquals(hangers, plan(dry, kit(Equipment.DEHUMIDIFIER)).first())
        assertEquals(hangers, plan(dry, kit(Equipment.TUMBLE_DRYER)).first())
        assertEquals(hangers, plan(dry, kit(Equipment.DEHUMIDIFIER, Equipment.TUMBLE_DRYER)).first())
    }

    @Test
    fun aDehumidifierRunsInAClosedRoomOnAHumidDay() {
        val plan = plan(humid, kit(Equipment.AIRER, Equipment.DEHUMIDIFIER))
        assertEquals(listOf(airer, dehumidifierRoom, radiator, turn), plan)
    }

    @Test
    fun withoutADehumidifierAHumidDayAirsAWarmRoom() {
        assertEquals(listOf(airer, warmRoom, radiator, turn), plan(humid, kit(Equipment.AIRER)))
        assertEquals(listOf(heatedAirer, warmRoom, radiator, turn), plan(humid, kit(Equipment.HEATED_AIRER)))
    }

    @Test
    fun notSaidKeepsTheOldHumidDayStep() {
        assertEquals(listOf(airer, dehumidifierOrRoom, radiator, turn), plan(humid, null))
    }

    @Test
    fun theSeventyPercentLineHoldsForEveryKitState() {
        val below = rainFrom11(69.9)
        val at = rainFrom11(70.0)
        assertEquals(listOf(airer, window, radiator, turn), plan(below, null))
        assertEquals(listOf(airer, window, radiator, turn), plan(below, kit(Equipment.AIRER)))
        assertEquals(listOf(airer, dehumidifierOrRoom, radiator, turn), plan(at, null))
        assertEquals(listOf(airer, warmRoom, radiator, turn), plan(at, kit(Equipment.AIRER)))
        assertEquals(
            listOf(airer, dehumidifierRoom, radiator, turn),
            plan(at, kit(Equipment.AIRER, Equipment.DEHUMIDIFIER)),
        )
    }

    @Test
    fun theTumbleDryerStepComesLastAndOnlyWithATumbleDryer() {
        assertEquals(
            listOf(airer, window, radiator, turn, tumble),
            plan(dry, kit(Equipment.AIRER, Equipment.TUMBLE_DRYER)),
        )
        for (kit in kits) {
            for (f in listOf(dry, humid)) {
                assertEquals(Equipment.TUMBLE_DRYER in kit, tumble in plan(f, kit), "$kit")
            }
        }
        assertTrue(tumble !in plan(dry, null))
    }

    @Test
    fun anEmptyKitGivesHangersAWarmRoomOnAHumidDayAndNoTumbleDryer() {
        assertEquals(listOf(hangers, warmRoom, radiator, turn), plan(humid, kit()))
        assertEquals(listOf(hangers, window, radiator, turn), plan(dry, kit()))
    }

    @Test
    fun aDehumidifierOnADryDayIsNotMentioned() {
        val plan = plan(dry, kit(Equipment.AIRER, Equipment.DEHUMIDIFIER))
        assertEquals(listOf(airer, window, radiator, turn), plan)
        assertTrue(dehumidifierRoom !in plan && dehumidifierOrRoom !in plan)
    }

    @Test
    fun aLightLoadHasNoTurnStep() {
        assertEquals(
            listOf(airer, warmRoom, radiator),
            plan(forecast { _, _ -> raw }, kit(Equipment.AIRER), Load.LIGHT),
        )
    }

    @Test
    fun dontBotherUsesTheKitToo() {
        val f = forecast { _, _ -> raw }
        val day = assertNotNull(advise(f, Load.LIGHT, at(day0, 8 to 0), kit(Equipment.DEHUMIDIFIER)).first())
        assertEquals(Verdict.DONT_BOTHER, day.verdict)
        assertEquals(listOf(hangers, dehumidifierRoom, radiator), day.indoorPlan)
    }

    @Test
    fun missingIndoorHoursGiveNoHumidityStepWhateverTheKit() {
        assertEquals(Verdict.PARTLY_DRY, assertNotNull(noIndoorHours.today(Load.HEAVY)).verdict)
        for (kit in kits + listOf(null)) {
            val plan = plan(noIndoorHours, kit)
            assertTrue(plan.none { it in humidSteps }, "$kit: $plan")
            assertTrue(radiator in plan && turn in plan, "$kit: $plan")
        }
    }

    @Test
    fun aWillDryDayHasNoPlanWhateverTheKit() {
        for (kit in kits + listOf(null)) {
            val day = assertNotNull(advise(forecast(), Load.HEAVY, at(day0, 8 to 0), kit).first())
            assertEquals(Verdict.WILL_DRY, day.verdict)
            assertEquals(emptyList(), day.indoorPlan, "$kit")
        }
    }

    @Test
    fun aPlanIsInTheEnumsOrderWithAtMostFiveLines() {
        for (kit in kits + listOf(null)) {
            for (f in listOf(dry, humid, noIndoorHours)) {
                val plan = plan(f, kit)
                assertEquals(plan.sortedBy { it.ordinal }, plan)
                assertTrue(plan.size <= 5, "$kit: $plan")
                assertEquals(1, plan.count { it in setOf(heatedAirer, airer, hangers) }, "$kit: $plan")
                assertTrue(plan.count { it in humidSteps } <= 1, "$kit: $plan")
            }
        }
    }

    /** Every forecast the engine tests and the reference set offer, with the time to ask at. */
    private fun everyForecast(): List<Pair<Forecast, Instant>> {
        val synthetic =
            listOf(dry, humid, noIndoorHours, forecast(), forecast { _, _ -> raw }, forecast { _, _ -> grey })
        return synthetic.map { it to at(day0, 8 to 0) } + allReferenceCases.map { it.forecast to it.now }
    }

    @Test
    fun notSaidGivesBoltTwosPlanOnEveryDay() {
        var plans = 0
        for ((f, now) in everyForecast()) {
            for (load in Load.entries) {
                for (day in advise(f, load, now, null).filterNotNull()) {
                    val plan = day.indoorPlan
                    if (day.verdict == Verdict.WILL_DRY) {
                        assertEquals(emptyList(), plan)
                        continue
                    }
                    plans++
                    // Bolt 2's rules: an airer, at most one of the two air steps, the radiator line, then
                    // the turn step for a heavy load. Nothing from bolt 10.
                    val air = plan.filter { it == window || it == dehumidifierOrRoom }
                    val old = listOf(airer) + air + radiator + listOfNotNull(turn.takeIf { load == Load.HEAVY })
                    assertEquals(old, plan, "${day.date} $load")
                    // And its air step is the one a set kit's humidity step answers to.
                    val withKit = advise(f, load, now, kit(Equipment.AIRER)).filterNotNull().first {
                        it.date == day.date
                    }
                    val answered = air.map { if (it == window) window else warmRoom }
                    assertEquals(answered, withKit.indoorPlan.filter { it in humidSteps }, "${day.date} $load")
                }
            }
        }
        assertTrue(plans > 20, "$plans plans checked")
    }

    @Test
    fun theKitNeverChangesAnythingButThePlan() {
        for ((f, now) in everyForecast()) {
            for (load in Load.entries) {
                val base = advise(f, load, now, null)
                for (kit in kits) {
                    val with = advise(f, load, now, kit)
                    assertEquals(
                        base.map {
                            it?.copy(indoorPlan = emptyList())
                        },
                        with.map { it?.copy(indoorPlan = emptyList()) },
                    )
                }
            }
        }
    }
}
