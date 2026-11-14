package io.github.dryingday.forecast

import com.russhwolf.settings.MapSettings
import io.github.dryingday.engine.Forecast
import io.github.dryingday.engine.Load
import io.github.dryingday.engine.Reason
import io.github.dryingday.engine.Verdict
import io.github.dryingday.engine.advise
import io.github.dryingday.engine.day0
import io.github.dryingday.engine.forecast
import io.github.dryingday.engine.london
import io.github.dryingday.weather.Attribution
import io.github.dryingday.weather.Coordinates
import io.github.dryingday.weather.ForecastSource
import io.github.dryingday.weather.SourceError
import io.github.dryingday.weather.SourceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val here = Coordinates(30.12, -40.34)
private val there = Coordinates(-40.56, -130.78)

// 08:00 in London on the engine tests' first day.
private val start = Instant.parse("2025-06-14T07:00:00Z")
private val theForecast: Forecast = forecast(london, day0)

private class FakeSource : ForecastSource {
    override val id = "fake"
    override val attributions: List<Attribution> = emptyList()
    val calls = mutableListOf<Coordinates>()
    var cancelled = 0
    var result: SourceResult<Forecast> = SourceResult.Success(theForecast)

    /** When set, a call waits for it. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun forecast(at: Coordinates): SourceResult<Forecast> {
        calls += at
        try {
            gate?.await()
        } catch (e: CancellationException) {
            cancelled++
            throw e
        }
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class Harness(
    private val test: TestScope,
    val settings: MapSettings = MapSettings(),
    scope: CoroutineScope = test.backgroundScope,
) {
    val source = FakeSource()
    val cache = ForecastCache(MapSettings())

    /** Moves the clock without moving the scheduler, as setting the device's clock does. */
    var skew: Duration = Duration.ZERO
    val clock = object : Clock {
        override fun now() = start + test.testScheduler.currentTime.milliseconds + skew
    }
    val state = ForecastState(source, cache, settings, clock, scope)

    fun cached(age: Duration, at: Coordinates = here) = cache.put(at, clock.now() - age, theForecast)

    fun open(at: Coordinates = here) {
        state.setPlace(at)
        state.setOnScreen(true)
        test.runCurrent()
    }

    fun pass(duration: Duration) {
        test.advanceTimeBy(duration)
        test.runCurrent()
    }

    val showing get() = assertIs<ForecastStatus.Showing>(state.status)
}

class ForecastStateTest {
    @Test
    fun noCacheFetchesWhenTheScreenComesOn() = runTest {
        val h = Harness(this)

        h.open()

        assertEquals(listOf(here), h.source.calls)
        assertEquals(start, h.showing.fetchedAt)
        assertNotNull(h.cache.get(here))
    }

    @Test
    fun nothingIsFetchedWhileNotOnScreen() = runTest {
        val h = Harness(this)
        h.state.setPlace(here)

        h.pass(3.hours)

        assertEquals(emptyList(), h.source.calls)
        assertEquals(ForecastStatus.Loading, h.state.status)
    }

    @Test
    fun aForecastJustUnderAnHourOldIsLeftAlone() = runTest {
        val h = Harness(this)
        h.cached(1.hours - 1.milliseconds)

        h.open()

        assertEquals(emptyList(), h.source.calls)
    }

    @Test
    fun aForecastAnHourOldIsFetchedAgain() = runTest {
        val h = Harness(this)
        h.cached(1.hours)

        h.open()

        assertEquals(1, h.source.calls.size)
    }

    @Test
    fun aFetchTimeAfterNowIsShownAndFetchedAgain() = runTest {
        val h = Harness(this)
        h.cached((-5).minutes)
        h.source.gate = CompletableDeferred()

        h.open()

        assertEquals(1, h.source.calls.size)
        assertTrue(h.showing.refreshing)
        assertEquals(start + 5.minutes, h.showing.fetchedAt)
    }

    @Test
    fun aFetchTimeAfterNowIsNeverOutOfDate() = runTest {
        val h = Harness(this)
        h.cached((-30).hours)
        h.source.result = SourceResult.Failure(SourceError.UNREACHABLE)

        h.open()

        assertEquals(SourceError.UNREACHABLE, h.showing.lastError)
    }

    @Test
    fun comingBackOnScreenFetchesAnOldForecast() = runTest {
        val h = Harness(this)
        h.open()
        h.state.setOnScreen(false)
        h.pass(90.minutes)
        assertEquals(1, h.source.calls.size)

        h.state.setOnScreen(true)
        h.runCurrent()

        assertEquals(2, h.source.calls.size)
    }

    private fun Harness.runCurrent() = pass(Duration.ZERO)

    @Test
    fun theMinuteTickFetchesWhenTheForecastTurnsAnHourOld() = runTest {
        val h = Harness(this)
        h.cached(59.minutes)
        h.open()
        assertEquals(0, h.source.calls.size)

        h.pass(1.minutes)

        assertEquals(1, h.source.calls.size)
    }

    @Test
    fun aNewCurrentPlaceIsFetchedOnScreen() = runTest {
        val h = Harness(this)
        h.open()

        h.state.setPlace(there)
        h.runCurrent()

        assertEquals(listOf(here, there), h.source.calls)
    }

    @Test
    fun aFailedAutomaticFetchWaitsFifteenMinutes() = runTest {
        val h = Harness(this)
        h.source.result = SourceResult.Failure(SourceError.SERVICE)
        h.open()
        assertEquals(1, h.source.calls.size)

        h.pass(14.minutes)
        assertEquals(1, h.source.calls.size)

        h.pass(1.minutes)
        assertEquals(2, h.source.calls.size)
    }

    @Test
    fun aFailedRefreshWaitsFifteenMinutesToo() = runTest {
        val h = Harness(this)
        h.cached(10.minutes)
        h.open()
        h.source.result = SourceResult.Failure(SourceError.SERVICE)
        h.state.refresh()
        h.runCurrent()
        assertEquals(1, h.source.calls.size)

        // Old enough for an automatic fetch by then, but for the wait.
        h.pass(14.minutes)
        h.cache.put(here, h.clock.now() - 2.hours, theForecast)
        h.pass(Duration.ZERO)
        assertEquals(1, h.source.calls.size)

        h.pass(1.minutes)
        assertEquals(2, h.source.calls.size)
    }

    @Test
    fun aWaitLongerThanFifteenMinutesMeansTheClockWentBackAndDoesNotHoldFetches() = runTest {
        val h = Harness(this)
        h.source.result = SourceResult.Failure(SourceError.UNREACHABLE)
        h.open()
        assertEquals(1, h.source.calls.size)

        h.skew = (-2).hours
        h.pass(1.minutes)

        assertEquals(2, h.source.calls.size)
    }

    @Test
    fun refreshIgnoresTheWaitAndANewPlaceClearsIt() = runTest {
        val h = Harness(this)
        h.source.result = SourceResult.Failure(SourceError.UNREACHABLE)
        h.open()

        h.state.refresh()
        h.runCurrent()
        assertEquals(2, h.source.calls.size)

        h.state.setPlace(there)
        h.runCurrent()
        assertEquals(listOf(here, here, there), h.source.calls)
    }

    @Test
    fun refreshDoesNothingWhileAFetchRuns() = runTest {
        val h = Harness(this)
        h.source.gate = CompletableDeferred()
        h.open()

        h.state.refresh()
        h.runCurrent()

        assertEquals(1, h.source.calls.size)
    }

    @Test
    fun leavingTheScreenCancelsAFetchAndStoresNothing() = runTest {
        val h = Harness(this)
        h.source.gate = CompletableDeferred()
        h.open()

        h.state.setOnScreen(false)
        h.source.gate?.complete(Unit)
        h.runCurrent()

        assertEquals(1, h.source.cancelled)
        assertNull(h.cache.get(here))
        assertEquals(ForecastStatus.Loading, h.state.status)
    }

    @Test
    fun aChangeOfPlaceCancelsAFetchAndStoresNothing() = runTest {
        val h = Harness(this)
        h.source.gate = CompletableDeferred()
        h.open()

        h.state.setPlace(there)
        h.runCurrent()
        assertEquals(1, h.source.cancelled)
        h.source.gate?.complete(Unit)
        h.runCurrent()

        assertNull(h.cache.get(here))
        assertNotNull(h.cache.get(there))
    }

    @Test
    fun aFailedRefreshKeepsTheTimeOfTheLastSuccessfulFetch() = runTest {
        val h = Harness(this)
        h.open()
        val fetchedAt = h.showing.fetchedAt
        h.source.result = SourceResult.Failure(SourceError.UNREACHABLE)

        h.pass(7.minutes)
        h.state.refresh()
        h.runCurrent()

        assertEquals(fetchedAt, h.showing.fetchedAt)
        assertEquals(fetchedAt, h.cache.get(here)?.fetchedAt)
        assertEquals(SourceError.UNREACHABLE, h.showing.lastError)
        assertEquals(start + 7.minutes, h.state.now)
    }

    @Test
    fun aFailedRefreshRaisesOneNoticeAndDismissingClearsIt() = runTest {
        val h = Harness(this)
        h.cached(10.minutes)
        h.open()
        assertNull(h.state.refreshNotice)
        h.source.result = SourceResult.Failure(SourceError.SERVICE)

        h.state.refresh()
        h.runCurrent()

        assertEquals(SourceError.SERVICE, h.state.refreshNotice?.error)
        h.state.dismissRefreshNotice()
        assertNull(h.state.refreshNotice)
    }

    @Test
    fun anAutomaticFailureRaisesNoNotice() = runTest {
        val h = Harness(this)
        h.cached(2.hours)
        h.source.result = SourceResult.Failure(SourceError.UNREACHABLE)

        h.open()

        assertEquals(SourceError.UNREACHABLE, h.showing.lastError)
        assertNull(h.state.refreshNotice)
    }

    @Test
    fun aFailedRefreshWithNoForecastToKeepRaisesNoNotice() = runTest {
        val h = Harness(this)
        h.source.result = SourceResult.Failure(SourceError.UNREACHABLE)
        h.open()

        h.state.refresh()
        h.runCurrent()

        assertNull(h.state.refreshNotice)
    }

    @Test
    fun aSuccessfulRefreshRaisesNoNoticeAndClearsOldOnes() = runTest {
        val h = Harness(this)
        h.cached(10.minutes)
        h.open()
        h.source.result = SourceResult.Failure(SourceError.UNREACHABLE)
        h.state.refresh()
        h.runCurrent()
        assertNotNull(h.state.refreshNotice)

        h.source.result = SourceResult.Success(theForecast)
        h.state.refresh()
        h.runCurrent()

        assertNull(h.state.refreshNotice)
    }

    @Test
    fun twoFailedRefreshesRaiseTwoDistinctNotices() = runTest {
        val h = Harness(this)
        h.cached(10.minutes)
        h.open()
        h.source.result = SourceResult.Failure(SourceError.UNREACHABLE)
        h.state.refresh()
        h.runCurrent()
        val first = h.state.refreshNotice
        h.state.refresh()
        h.runCurrent()

        assertNotEquals(first?.id, h.state.refreshNotice?.id)
    }

    @Test
    fun aFailureKeepsTheCachedForecastAndASuccessClearsTheError() = runTest {
        val h = Harness(this)
        h.cached(2.hours)
        h.source.result = SourceResult.Failure(SourceError.BAD_RESPONSE)
        h.open()
        assertEquals(SourceError.BAD_RESPONSE, h.showing.lastError)
        assertEquals(start - 2.hours, h.showing.fetchedAt)

        h.source.result = SourceResult.Success(theForecast)
        h.state.refresh()
        h.runCurrent()

        assertNull(h.showing.lastError)
        assertEquals(start + 0.milliseconds, h.showing.fetchedAt)
    }

    @Test
    fun aForecastIsOutOfDateAtTwentyFourHours() = runTest {
        val h = Harness(this)
        h.source.result = SourceResult.Failure(SourceError.UNREACHABLE)
        h.cached(24.hours - 1.minutes)
        h.open()
        assertIs<ForecastStatus.Showing>(h.state.status)

        // The tick is what moves the age past the line.
        h.pass(1.minutes)

        val out = assertIs<ForecastStatus.OutOfDate>(h.state.status)
        assertEquals(SourceError.UNREACHABLE, out.lastError)
    }

    @Test
    fun statusIsLoadingThenFailedWithNothingCached() = runTest {
        val h = Harness(this)
        h.source.gate = CompletableDeferred()
        h.source.result = SourceResult.Failure(SourceError.UNREACHABLE)
        h.open()
        assertEquals(ForecastStatus.Loading, h.state.status)

        h.source.gate?.complete(Unit)
        h.runCurrent()

        assertEquals(ForecastStatus.Failed(SourceError.UNREACHABLE), h.state.status)
    }

    @Test
    fun theTickMovesTodayPastABringInAndSunset() = runTest {
        val h = Harness(this)
        h.cached(Duration.ZERO)
        h.open()
        val before = checkNotNull(h.showing.days[0])
        assertEquals(Verdict.WILL_DRY, before.verdict)

        h.pass(checkNotNull(before.bringIn) - h.clock.now() + 1.minutes)
        val afterBringIn = h.showing.days[0]
        assertNotEquals(before, afterBringIn)

        h.pass(Instant.parse("2025-06-14T19:01:00Z") - h.clock.now())
        val afterSunset = checkNotNull(h.showing.days[0])
        assertEquals(Verdict.DONT_BOTHER, afterSunset.verdict)
        assertEquals(listOf(Reason.DAY_OVER), afterSunset.reasons)
    }

    @Test
    fun theTickMovesTodayPastMidnightInTheForecastsZone() = runTest {
        val h = Harness(this)
        h.cached(Duration.ZERO)
        h.open()
        assertEquals(LocalDate(2025, 6, 14), h.showing.today)

        // 23:00 UTC is already past midnight in London (BST).
        h.pass(Instant.parse("2025-06-14T22:59:00Z") - h.clock.now())
        assertEquals(LocalDate(2025, 6, 14), h.showing.today)
        h.pass(1.minutes)

        assertEquals(LocalDate(2025, 6, 15), h.showing.today)
        assertNull(h.showing.days[2])
    }

    @Test
    fun theLoadDefaultsToHeavyAndIsRemembered() = runTest {
        val settings = MapSettings()
        val first = Harness(this, settings)
        assertEquals(Load.HEAVY, first.state.load)

        first.state.chooseLoad(Load.LIGHT)

        assertEquals("LIGHT", settings.getStringOrNull("load"))
        assertEquals(Load.LIGHT, Harness(this, settings).state.load)
    }

    @Test
    fun anUnknownStoredLoadCountsAsTheDefault() = runTest {
        assertEquals(Load.HEAVY, Harness(this, MapSettings("load" to "MEDIUM")).state.load)
    }

    @Test
    fun switchingTheLoadRerunsTheEngineAndFetchesNothing() = runTest {
        val h = Harness(this)
        h.open()
        val heavy = h.showing.days

        h.state.chooseLoad(Load.LIGHT)

        assertEquals(advise(theForecast, Load.LIGHT, h.clock.now()), h.showing.days)
        assertNotEquals(emptyList(), heavy)
        assertEquals(1, h.source.calls.size)
    }

    @Suppress("InjectDispatcher") // The point: a scope that runs launched work at once.
    @Test
    fun anEagerDispatcherDoesNotLeaveTheFetchLookingBusy() = runTest {
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val h = Harness(this, scope = scope)
        h.state.setPlace(here)

        h.state.refresh()
        assertFalse(h.showing.refreshing)
        h.state.refresh()

        assertEquals(2, h.source.calls.size)
        scope.cancel()
    }

    @Test
    fun comingBackOnScreenRerunsTheAdviceAtOnce() = runTest {
        val h = Harness(this)
        h.open()
        h.state.setOnScreen(false)

        h.skew = 25.hours
        h.state.setOnScreen(true)

        assertIs<ForecastStatus.OutOfDate>(h.state.status)
    }
}
