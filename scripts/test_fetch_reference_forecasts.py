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

    def test_supersaturated_humidity_is_clamped_to_100_before_averaging(self):
        # Open-Meteo gave 102 % in saturated air (Greenwich, 10 January 2025).
        data = response([T13, T13 + H], relative_humidity_2m=[102, 90])
        (hour,) = ref.engine_hours(data)
        self.assertEqual(95.0, hour["relativeHumidityPercent"])

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


OSLO = ZoneInfo("Europe/Oslo")


def daily(offset_s, *days):
    """Open-Meteo's daily block: (time, sunrise, sunset, daylight seconds) per day."""
    time, sunrise, sunset, daylight = (list(column) for column in zip(*days))
    return {"utc_offset_seconds": offset_s,
            "daily": {"time": time, "sunrise": sunrise, "sunset": sunset, "daylight_duration": daylight}}


class DaysAndTimesTest(unittest.TestCase):
    def test_an_ordinary_day_is_dated_by_its_sunrise_in_the_real_zone(self):
        # 14 June 2025 in London. Open-Meteo's day starts at midnight UTC, its fixed offset.
        data = daily(0, (1749859200, 1749872548, 1749932303, 59746.4))
        (day,) = ref.engine_days(data, ZoneInfo("Europe/London"))
        self.assertEqual((date(2025, 6, 14), 1749872548, 1749932303), (day["date"], day["sunrise"], day["sunset"]))

    def test_polar_day_runs_from_real_local_midnight_to_midnight(self):
        # Open-Meteo fetched at GMT+1 gives 18 May as 00:00 GMT+1 to 00:00 GMT+1, which is 01:00 in Oslo.
        data = daily(3600, (1747522800, 1747522800, 1747609200, 86400.0))
        (day,) = ref.engine_days(data, OSLO)
        self.assertEqual(date(2025, 5, 18), day["date"])
        # Midnight CEST is 22:00 UTC.
        self.assertEqual((1747519200, 1747605600), (day["sunrise"], day["sunset"]))

    def test_polar_night_is_an_empty_day_at_real_local_midnight(self):
        # 28 November 2025, and a day Open-Meteo gives seven minutes of sun but zero daylight.
        data = daily(3600, (1764284400, 1764284400, 1764284400, 0.0), (1764370800, 1764406072, 1764406516, 0.0))
        first, second = ref.engine_days(data, OSLO)
        self.assertEqual((date(2025, 11, 28), 1764284400, 1764284400), (first["date"], first["sunrise"], first["sunset"]))
        self.assertEqual((date(2025, 11, 29), 1764370800, 1764370800), (second["date"], second["sunrise"], second["sunset"]))

    def test_a_missing_sunrise_or_a_repeated_date_is_an_error(self):
        with self.assertRaises(ValueError):
            ref.engine_days(daily(0, (1749859200, None, 1749932303, 59746.4)), ZoneInfo("Europe/London"))
        same = (1749859200, 1749872548, 1749932303, 59746.4)
        with self.assertRaises(ValueError):
            ref.engine_days(daily(0, same, same), ZoneInfo("Europe/London"))

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

    def test_the_case_function_name_is_a_valid_identifier_for_any_id(self):
        self.assertEqual("case_greenwich_2025_06_19_0800", ref.kotlin_name("greenwich-2025-06-19-0800"))
        self.assertEqual("case_new_york_2025_01_01_0800", ref.kotlin_name("new-york-2025-01-01-0800"))

    def test_finite_floats_only(self):
        with self.assertRaises(ValueError):
            ref.kotlin_doubles([math.inf])


def label(verdict="WILL_DRY", hang="08:00", bring="11:00"):
    return {"verdict": verdict, "hangOut": hang, "bringIn": bring, "reason": "r"}


class ExpectationsTest(unittest.TestCase):
    cases = [{"id": "a"}, {"id": "b"}]
    labels = {"labels": [{"id": "a", "light": label(), "heavy": label(bring="14:00")},
                         {"id": "b", "light": label(), "heavy": label("PARTLY_DRY", "08:00", "15:00")}]}

    def test_without_decisions_every_label_stands(self):
        out = ref.expectations(self.cases, self.labels, {"decisions": []})
        self.assertEqual(4, len(out))
        self.assertEqual({"AGREED"}, {e["decision"] for e in out})
        self.assertEqual(("a", "heavy", "WILL_DRY", "08:00", "14:00"),
                         tuple(out[1][k] for k in ("id", "load", "verdict", "hangOut", "bringIn")))

    def test_a_wrong_label_is_replaced_and_an_open_one_kept_but_marked(self):
        decisions = {"decisions": [
            {"id": "b", "load": "heavy", "decision": "label wrong", "verdict": "DONT_BOTHER", "hangOut": None, "bringIn": None},
            {"id": "a", "load": "light", "decision": "open"},
        ]}
        out = {(e["id"], e["load"]): e for e in ref.expectations(self.cases, self.labels, decisions)}
        self.assertEqual(("LABEL_WRONG", "DONT_BOTHER", None, None),
                         tuple(out[("b", "heavy")][k] for k in ("decision", "verdict", "hangOut", "bringIn")))
        self.assertEqual("OPEN", out[("a", "light")]["decision"])

    def test_decisions_must_match_a_label_once_and_be_well_formed(self):
        bad = [
            [{"id": "zzz", "load": "light", "decision": "open"}],
            [{"id": "a", "load": "light", "decision": "open"}, {"id": "a", "load": "light", "decision": "open"}],
            [{"id": "a", "load": "light", "decision": "maybe"}],
            [{"id": "a", "load": "light", "decision": "label wrong", "verdict": "WILL_DRY", "hangOut": None, "bringIn": None}],
        ]
        for decisions in bad:
            with self.assertRaises(ValueError, msg=decisions):
                ref.expectations(self.cases, self.labels, {"decisions": decisions})

    def test_every_case_needs_a_label(self):
        with self.assertRaises(ValueError):
            ref.expectations(self.cases + [{"id": "c"}], self.labels, {"decisions": []})

    def test_the_kotlin_names_the_load_verdict_and_decision(self):
        out = ref.expectations(self.cases[:1], {"labels": self.labels["labels"][:1]}, {"decisions": []})
        kotlin = ref.kotlin_expectations_file(out)
        self.assertIn('ReferenceExpectation("a", Load.LIGHT, Verdict.WILL_DRY, "08:00", "11:00", Decision.AGREED)', kotlin)


if __name__ == "__main__":
    unittest.main()
