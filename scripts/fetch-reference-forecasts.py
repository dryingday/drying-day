#!/usr/bin/env python3
"""Fetch the drying rules' reference forecasts and generate their Kotlin.

Reads the case list in shared/src/commonTest/testdata/reference/cases.json: public landmarks only,
coordinates to two decimal places (see CLAUDE.md and docs/bolts/02-drying-rules.md). For each case it
fetches the past forecast from Open-Meteo's historical-forecast API, from the day before the case to three
days after, and saves it next to the case list. Then it writes ReferenceForecasts.kt, which holds every
case as an engine Forecast, since common tests can't read files.

The mapping onto the engine's hours follows the spec: the hour from `start` takes the preceding-hour values
(precipitation, its probability, gusts, radiation) stamped `start + 1 h`, and the mean of the
instantaneous values (temperature, humidity, wind speed, visibility) at `start` and `start + 1 h`. An hour
missing a required value is dropped, as bolt 3's mapping must do. Relative humidity is clamped to 100 %:
models report a little over it in saturated air.

Run from the repository root:
    python3 scripts/fetch-reference-forecasts.py            # fetch, then generate
    python3 scripts/fetch-reference-forecasts.py --offline  # generate from the saved responses
"""

import argparse
import json
import math
import re
import sys
import urllib.parse
import urllib.request
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

TESTDATA = Path("shared/src/commonTest/testdata/reference")
CASES = TESTDATA / "cases.json"
KOTLIN = Path("shared/src/commonTest/kotlin/io/github/dryingday/reference/ReferenceForecasts.kt")
API = "https://historical-forecast-api.open-meteo.com/v1/forecast"
TIMEOUT_S = 30

# Open-Meteo field -> engine field. Instantaneous at the stamp, or covering the hour before it.
INSTANT = {
    "temperature_2m": "temperatureC",
    "relative_humidity_2m": "relativeHumidityPercent",
    "wind_speed_10m": "windSpeedKmh",
    "visibility": "visibilityM",
}
PRECEDING = {
    "precipitation_probability": "precipitationProbabilityPercent",
    "precipitation": "precipitationMm",
    "wind_gusts_10m": "windGustKmh",
    "shortwave_radiation": "shortwaveRadiationWm2",
}
NULLABLE = {"visibilityM", "precipitationProbabilityPercent"}
# In the order of HourlyWeather's constructor after `start`.
ENGINE_FIELDS = [
    "temperatureC", "relativeHumidityPercent", "windSpeedKmh", "visibilityM",
    "precipitationProbabilityPercent", "precipitationMm", "windGustKmh", "shortwaveRadiationWm2",
]
HOUR_S = 3600
MAX_HUMIDITY_PERCENT = 100.0


def fetch(case):
    day = date.fromisoformat(case["date"])
    query = urllib.parse.urlencode({
        "latitude": case["latitude"],
        "longitude": case["longitude"],
        "start_date": (day - timedelta(days=1)).isoformat(),
        "end_date": (day + timedelta(days=3)).isoformat(),
        "hourly": ",".join([*INSTANT, *PRECEDING]),
        "daily": "sunrise,sunset",
        "timezone": "auto",
        "timeformat": "unixtime",
    })
    with urllib.request.urlopen(f"{API}?{query}", timeout=TIMEOUT_S) as reply:
        return clean(json.load(reply))


def clean(data):
    """Round the echoed grid coordinates to two decimals, and drop what changes on every fetch."""
    data = dict(data)
    data.pop("generationtime_ms", None)
    for key in ("latitude", "longitude"):
        if key in data:
            data[key] = round(data[key], 2)
    return data


def engine_hours(data):
    hourly = data["hourly"]
    times = hourly["time"]
    hours = []
    for i in range(len(times) - 1):
        if times[i + 1] - times[i] != HOUR_S:
            continue
        hour = {"start": times[i]}
        for source, field in INSTANT.items():
            a, b = hourly[source][i], hourly[source][i + 1]
            hour[field] = None if a is None or b is None else (a + b) / 2
        if hour["relativeHumidityPercent"] is not None:
            hour["relativeHumidityPercent"] = min(hour["relativeHumidityPercent"], MAX_HUMIDITY_PERCENT)
        for source, field in PRECEDING.items():
            hour[field] = hourly[source][i + 1]
        if all(hour[f] is not None for f in ENGINE_FIELDS if f not in NULLABLE):
            hours.append(hour)
    return hours


def engine_days(data, zone):
    days = {}
    for sunrise, sunset in zip(data["daily"]["sunrise"], data["daily"]["sunset"]):
        if sunrise is None or sunset is None:
            continue
        local = datetime.fromtimestamp(sunrise, zone).date()
        days.setdefault(local, {"date": local, "sunrise": sunrise, "sunset": sunset})
    return [days[d] for d in sorted(days)]


def now_epoch(day, clock, zone):
    local = datetime.combine(date.fromisoformat(day), time.fromisoformat(clock), zone)
    return int(local.astimezone(timezone.utc).timestamp())


def kotlin_doubles(values):
    parts = []
    for v in values:
        if v is None:
            parts.append("Double.NaN")
        elif not math.isfinite(v):
            raise ValueError(f"not a finite number: {v}")
        else:
            parts.append(repr(float(v)))
    return f"doubleArrayOf({', '.join(parts)})"


def kotlin_name(case_id):
    return re.sub(r"[^a-z0-9]", "", case_id.replace("-", "", 3).replace("-", "at", 1))


def kotlin_string(text):
    return json.dumps(text, ensure_ascii=False).replace("$", "\\$")


def kotlin_case(case, data):
    zone = ZoneInfo(data["timezone"])
    hours = engine_hours(data)
    days = engine_days(data, zone)
    y, m, d = (int(p) for p in case["date"].split("-"))
    lines = [
        f"private fun {kotlin_name(case['id'])}() = ReferenceCase(",
        f"    id = {kotlin_string(case['id'])},",
        f"    landmark = {kotlin_string(case['landmark'])},",
        f"    date = LocalDate({y}, {m}, {d}),",
        f"    now = Instant.fromEpochSeconds({now_epoch(case['date'], case['now'], zone)}),",
        "    forecast = referenceForecast(",
        f"        zone = {kotlin_string(data['timezone'])},",
        f"        starts = longArrayOf({', '.join(str(h['start']) for h in hours)}),",
    ]
    for field in ENGINE_FIELDS:
        lines.append(f"        {field} = {kotlin_doubles([h[field] for h in hours])},")
    lines.append("        days = listOf(")
    for day in days:
        dd = day["date"]
        lines.append(f"            referenceDay({dd.year}, {dd.month}, {dd.day}, {day['sunrise']}, {day['sunset']}),")
    lines += ["        ),", "    ),", ")"]
    return "\n".join(lines)


def kotlin_file(cases, responses):
    names = [kotlin_name(c["id"]) for c in cases]
    head = [
        "// Generated by scripts/fetch-reference-forecasts.py from shared/src/commonTest/testdata/reference.",
        "// Don't edit: change the case list and run the script. Weather data by Open-Meteo.com, CC BY 4.0;",
        "// see testdata/reference/README.md.",
        "@file:Suppress(\"ktlint\", \"detekt:all\")",
        "",
        "package io.github.dryingday.reference",
        "",
        "import kotlinx.datetime.LocalDate",
        "import kotlin.time.Instant",
        "",
        "/** Every reference case, in the order of cases.json. One function each, to keep methods small. */",
        "internal fun referenceCases(): List<ReferenceCase> = listOf(",
        *[f"    {n}()," for n in names],
        ")",
    ]
    body = [kotlin_case(c, responses[c["id"]]) for c in cases]
    return "\n".join(head) + "\n\n" + "\n\n".join(body) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--offline", action="store_true", help="generate from the saved responses only")
    args = parser.parse_args()
    cases = json.loads(CASES.read_text())["cases"]
    responses = {}
    for case in cases:
        path = TESTDATA / f"{case['id']}.json"
        if not args.offline:
            print(f"fetching {case['id']}", file=sys.stderr)
            path.write_text(json.dumps(fetch(case), separators=(",", ":")) + "\n")
        responses[case["id"]] = json.loads(path.read_text())
    KOTLIN.write_text(kotlin_file(cases, responses))
    print(f"wrote {KOTLIN} with {len(cases)} cases", file=sys.stderr)


if __name__ == "__main__":
    main()
