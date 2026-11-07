"""Tests for fetch-reference-forecasts.py. Run: python3 -m unittest discover -s scripts"""

import importlib.util
import math
import unittest
from datetime import date
from pathlib import Path
from zoneinfo import ZoneInfo

_spec = importlib.util.spec_from_file_location(
    "fetch_reference_forecasts", Path(__file__).with_name("fetch-reference-forecasts.py"))
ref = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ref)

T13 = 1749906000  # 2025-06-14T13:00:00Z
H = 3600


def response(times, **overrides):
    """An Open-Meteo-shaped response with steady weather, and overrides per field."""
    n = len(times)
    hourly = {"time": times}
    defaults = {
        "temperature_2m": 20.0, "relative_humidity_2m": 60, "wind_speed_10m": 10.0, "visibility": 20000.0,
        "precipitation_probability": 0, "precipitation": 0.0, "wind_gusts_10m": 20.0, "shortwave_radiation": 400.0,
    }
    for key, value in defaults.items():
        hourly[key] = overrides.get(key, [value] * n)
    return {"timezone": "Europe/London", "hourly": hourly, "daily": {"sunrise": [], "sunset": []}}


class EngineHoursTest(unittest.TestCase):
    def test_a_shower_stamped_14_00_lands_in_the_hour_from_13_00(self):
        data = response([T13, T13 + H, T13 + 2 * H], precipitation=[0.0, 1.2, 0.0],
                        precipitation_probability=[0, 80, 0])
        hours = ref.engine_hours(data)
        self.assertEqual([T13, T13 + H], [h["start"] for h in hours])
        self.assertEqual(1.2, hours[0]["precipitationMm"])
        self.assertEqual(80, hours[0]["precipitationProbabilityPercent"])
        self.assertEqual(0.0, hours[1]["precipitationMm"])

    def test_gusts_and_radiation_also_come_from_the_end_of_the_hour(self):
        data = response([T13, T13 + H], wind_gusts_10m=[10.0, 45.0], shortwave_radiation=[100.0, 700.0])
        (hour,) = ref.engine_hours(data)
        self.assertEqual(45.0, hour["windGustKmh"])
        self.assertEqual(700.0, hour["shortwaveRadiationWm2"])

    def test_instantaneous_values_are_the_mean_of_both_ends(self):
        data = response([T13, T13 + H], temperature_2m=[18.0, 21.0], relative_humidity_2m=[70, 60],
                        wind_speed_10m=[10.0, 15.0], visibility=[10000.0, 20000.0])
        (hour,) = ref.engine_hours(data)
        self.assertEqual(19.5, hour["temperatureC"])
        self.assertEqual(65.0, hour["relativeHumidityPercent"])
        self.assertEqual(12.5, hour["windSpeedKmh"])
        self.assertEqual(15000.0, hour["visibilityM"])

    def test_supersaturated_humidity_is_clamped_to_100(self):
        # Open-Meteo gave 102 % in saturated air (Greenwich, 10 January 2025).
        data = response([T13, T13 + H], relative_humidity_2m=[102, 100])
        (hour,) = ref.engine_hours(data)
        self.assertEqual(100.0, hour["relativeHumidityPercent"])

    def test_a_missing_nullable_value_is_kept_as_null(self):
        data = response([T13, T13 + H], visibility=[None, 20000.0], precipitation_probability=[0, None])
        (hour,) = ref.engine_hours(data)
        self.assertIsNone(hour["visibilityM"])
        self.assertIsNone(hour["precipitationProbabilityPercent"])

    def test_an_hour_missing_a_required_value_is_dropped(self):
        data = response([T13, T13 + H, T13 + 2 * H], temperature_2m=[18.0, None, 20.0])
        self.assertEqual([], ref.engine_hours(data))
        data = response([T13, T13 + H, T13 + 2 * H], precipitation=[0.0, None, 0.0])
        self.assertEqual([T13 + H], [h["start"] for h in ref.engine_hours(data)])

    def test_stamps_that_are_not_an_hour_apart_make_no_hour(self):
        data = response([T13, T13 + 2 * H, T13 + 3 * H])
        self.assertEqual([T13 + 2 * H], [h["start"] for h in ref.engine_hours(data)])


class DaysAndTimesTest(unittest.TestCase):
    def test_each_day_is_dated_by_its_sunrise_in_the_real_zone(self):
        # Open-Meteo's polar day at its fixed offset: midnight GMT+1, which is 01:00 in Oslo in summer.
        data = {"daily": {"sunrise": [1747522800], "sunset": [1747609200]}}
        (day,) = ref.engine_days(data, ZoneInfo("Europe/Oslo"))
        self.assertEqual(date(2025, 5, 18), day["date"])
        self.assertEqual((1747522800, 1747609200), (day["sunrise"], day["sunset"]))

    def test_now_is_local_clock_time_on_the_case_date(self):
        # 08:00 on the day the clocks go forward in London is 07:00 UTC.
        self.assertEqual(1743318000, ref.now_epoch("2025-03-30", "08:00", ZoneInfo("Europe/London")))
        # 08:00 in January is 08:00 UTC.
        self.assertEqual(1736496000, ref.now_epoch("2025-01-10", "08:00", ZoneInfo("Europe/London")))

    def test_the_echoed_coordinates_are_rounded_to_two_decimals(self):
        data = {"latitude": 51.472404, "longitude": -0.0108337, "generationtime_ms": 0.12}
        cleaned = ref.clean(data)
        self.assertEqual((51.47, -0.01), (cleaned["latitude"], cleaned["longitude"]))
        self.assertNotIn("generationtime_ms", cleaned)


class KotlinTest(unittest.TestCase):
    def test_nullable_values_are_written_as_nan_and_numbers_exactly(self):
        self.assertEqual("doubleArrayOf(1.5, Double.NaN, 20.0)", ref.kotlin_doubles([1.5, None, 20]))

    def test_the_case_function_name_is_a_valid_identifier(self):
        self.assertEqual("greenwich20250619at0800", ref.kotlin_name("greenwich-2025-06-19-0800"))

    def test_finite_floats_only(self):
        with self.assertRaises(ValueError):
            ref.kotlin_doubles([math.inf])


if __name__ == "__main__":
    unittest.main()
