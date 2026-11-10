package io.github.dryingday.firstrun

import com.russhwolf.settings.MapSettings
import io.github.dryingday.location.DeviceLocation
import io.github.dryingday.location.DeviceLocationResult
import io.github.dryingday.places.PlaceStore
import io.github.dryingday.places.SavedPlace
import io.github.dryingday.weather.Coordinates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val atlantic = Coordinates(30.1234, -40.3412)

private class FakeLocation : DeviceLocation {
    var calls = 0
    var settingsOpened = 0
    var answer = CompletableDeferred<DeviceLocationResult>()

    override suspend fun locate(): DeviceLocationResult {
        calls++
        return answer.await()
    }

    override fun openAppSettings() {
        settingsOpened++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FirstRunStateTest {
    private val location = FakeLocation()
    private val store = PlaceStore(MapSettings())
    private var done = 0
    private val state = FirstRunState(location, store) { done++ }

    private fun TestScope.tap() = launch { state.useLocation() }

    private suspend fun TestScope.locateWith(result: DeviceLocationResult) {
        val job = tap()
        runCurrent()
        location.answer.complete(result)
        job.join()
    }

    @Test
    fun startsReadyWithNoMessageAndDoesNotLocate() {
        assertFalse(state.waiting)
        assertNull(state.problem)
        assertEquals(0, location.calls)
    }

    @Test
    fun permissionDeniedShowsItsMessageAndStays() = runTest {
        locateWith(DeviceLocationResult.PermissionDenied)

        assertEquals(LocationProblem.PermissionDenied, state.problem)
        assertFalse(state.waiting)
        assertEquals(0, done)
        assertNull(store.current)
    }

    @Test
    fun locationOffShowsItsMessageAndStays() = runTest {
        locateWith(DeviceLocationResult.LocationOff)

        assertEquals(LocationProblem.LocationOff, state.problem)
        assertEquals(0, done)
        assertNull(store.current)
    }

    @Test
    fun noFixShowsItsMessageAndStays() = runTest {
        locateWith(DeviceLocationResult.NoFix)

        assertEquals(LocationProblem.NoFix, state.problem)
        assertEquals(0, done)
        assertNull(store.current)
    }

    @Test
    fun interruptedReturnsToReadyWithNoMessage() = runTest {
        locateWith(DeviceLocationResult.Interrupted)

        assertNull(state.problem)
        assertFalse(state.waiting)
        assertEquals(0, done)
        assertNull(store.current)
    }

    @Test
    fun interruptedClearsAnEarlierMessage() = runTest {
        locateWith(DeviceLocationResult.NoFix)
        location.answer = CompletableDeferred()
        locateWith(DeviceLocationResult.Interrupted)

        assertNull(state.problem)
    }

    @Test
    fun waitsDuringTheFixAndASecondTapDoesNotLocateAgain() = runTest {
        val first = tap()
        runCurrent()
        assertTrue(state.waiting)

        val second = tap()
        runCurrent()
        second.join()

        assertEquals(1, location.calls)
        location.answer.complete(DeviceLocationResult.NoFix)
        first.join()
        assertFalse(state.waiting)
    }

    @Test
    fun aNewTapClearsTheOldMessageWhileWaiting() = runTest {
        locateWith(DeviceLocationResult.NoFix)
        location.answer = CompletableDeferred()

        val job = tap()
        runCurrent()

        assertNull(state.problem)
        assertTrue(state.waiting)
        job.cancel()
    }

    @Test
    fun foundSavesTheDevicePlaceRoundedAndGoesOn() = runTest {
        locateWith(DeviceLocationResult.Found(atlantic))

        assertEquals(SavedPlace.Device(Coordinates(30.12, -40.34)), store.current)
        assertEquals(1, store.places.size)
        assertEquals(1, done)
        assertFalse(state.waiting)
        assertNull(state.problem)
    }

    @Test
    fun leavingTheScreenCancelsTheFixAndSavesNothing() = runTest {
        val job = tap()
        runCurrent()
        assertTrue(state.waiting)

        job.cancel()
        job.join()

        assertFalse(state.waiting)
        assertNull(state.problem)
        assertNull(store.current)
        assertEquals(0, done)
    }

    @Test
    fun theSettingsLinkOpensTheAppSettings() {
        state.openSettings()

        assertEquals(1, location.settingsOpened)
    }

    @Test
    fun openingSettingsClearsThePermissionMessage() = runTest {
        locateWith(DeviceLocationResult.PermissionDenied)

        state.openSettings()

        assertNull(state.problem)
    }

    @Test
    fun withoutALocationTheChoiceIsNotOfferedAndNothingHappens() = runTest {
        val bare = FirstRunState(null, store) { done++ }

        bare.useLocation()
        bare.openSettings()

        assertFalse(bare.canUseLocation)
        assertTrue(state.canUseLocation)
        assertFalse(bare.waiting)
        assertEquals(0, done)
    }
}
