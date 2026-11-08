#!/usr/bin/env python3
"""Fetch the drying rules' reference forecasts and generate their Kotlin.

Reads the case list in shared/src/commonTest/testdata/reference/cases.json: public landmarks only,
coordinates to two decimal places (see CLAUDE.md and docs/bolts/02-drying-rules.md). For each case it
fetches the past forecast from Open-Meteo's historical-forecast API, from the day before the case to three
days after, and saves it next to the case list. Then it writes ReferenceForecasts.kt, which holds every
case as an engine Forecast, since common tests can't read files, and ReferenceExpectations.kt, the blind
labels (labels.json) with the maintainer's decisions on disagreements (decisions.json) applied.

The mapping onto the engine's hours follows the spec: the hour from `start` takes the preceding-hour values
(precipitation, its probability, gusts, radiation) stamped `start + 1 h`, and the mean of the
instantaneous values (temperature, humidity, wind speed, visibility) at `start` and `start + 1 h`. An hour
missing a required value is dropped, as bolt 3's mapping must do. Each relative humidity value is clamped
to 100 % before averaging: models report a little over it in saturated air.

Open-Meteo gives every time in one fixed UTC offset per response, whatever the season. Ordinary days are
dated by their sunrise in the real IANA zone. Polar day and night come back as "midnight" at that fixed
offset, so they are rebuilt from the real local midnight.

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
LABELS = TESTDATA / "labels.json"
DECISIONS = TESTDATA / "decisions.json"
DOCUMENT = Path("docs/reference-set.md")
KOTLIN_EXPECTATIONS = Path("shared/src/commonTest/kotlin/io/github/dryingday/reference/ReferenceExpectations.kt")
LOADS = ("light", "heavy")
VERDICTS = ("WILL_DRY", "PARTLY_DRY", "DONT_BOTHER")
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
DAY_S = 86400
MAX_HUMIDITY_PERCENT = 100.0


def fetch(case):
    day = date.fromisoformat(case["date"])
    query = urllib.parse.urlencode({
        "latitude": case["latitude"],
        "longitude": case["longitude"],
        "start_date": (day - timedelta(days=1)).isoformat(),
        "end_date": (day + timedelta(days=3)).isoformat(),
        "hourly": ",".join([*INSTANT, *PRECEDING]),
        "daily": "sunrise,sunset,daylight_duration",
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
            if field == "relativeHumidityPercent":
                a, b = (None if v is None else min(v, MAX_HUMIDITY_PERCENT) for v in (a, b))
            hour[field] = None if a is None or b is None else (a + b) / 2
        for source, field in PRECEDING.items():
            hour[field] = hourly[source][i + 1]
        if all(hour[f] is not None for f in ENGINE_FIELDS if f not in NULLABLE):
            hours.append(hour)
    return hours


def engine_days(data, zone):
    daily = data["daily"]
    offset = timedelta(seconds=data["utc_offset_seconds"])
    days = {}
    for start, sunrise, sunset, daylight in zip(
            daily["time"], daily["sunrise"], daily["sunset"], daily["daylight_duration"]):
        if None in (start, sunrise, sunset, daylight):
            raise ValueError(f"a day with missing sun times: {start}")
        if daylight == 0 or daylight >= DAY_S:
            local = (datetime.fromtimestamp(start, timezone.utc) + offset).date()
            midnight = local_midnight(local, zone)
            sunrise, sunset = midnight, (local_midnight(local + timedelta(days=1), zone) if daylight else midnight)
        else:
            local = datetime.fromtimestamp(sunrise, zone).date()
        if local in days:
            raise ValueError(f"two days on {local}")
        days[local] = {"date": local, "sunrise": sunrise, "sunset": sunset}
    return [days[d] for d in sorted(days)]


def local_midnight(day, zone):
    return int(datetime.combine(day, time(), zone).timestamp())


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
    return "case_" + re.sub(r"[^A-Za-z0-9]", "_", case_id)


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


def expectations(cases, labels, decisions):
    """What ReferenceSetTest expects of each case and load: the label, unless the maintainer decided it was
    wrong (then the decided answer) or left it open (then the test skips it)."""
    by_id = {entry["id"]: entry for entry in labels["labels"]}
    decided = {}
    for d in decisions["decisions"]:
        key = (d["id"], d["load"])
        if d.get("id") not in by_id or d.get("load") not in LOADS or "decision" not in d:
            raise ValueError(f"a decision for no label: {key}")
        if key in decided:
            raise ValueError(f"two decisions for {key}")
        if d["decision"] == "label wrong":
            check_answer(d, key)
            if answer_of(d) == answer_of(by_id[d["id"]][d["load"]]):
                raise ValueError(f"a wrong label decided the same as the label: {key}")
        elif d["decision"] != "open":
            raise ValueError(f"unknown decision {d['decision']!r} for {key}")
        decided[key] = d
    unknown = set(by_id) - {case["id"] for case in cases}
    if unknown:
        raise ValueError(f"labels for no case: {sorted(unknown)}")
    out = []
    for case in cases:
        if case["id"] not in by_id:
            raise ValueError(f"no label for {case['id']}")
        for load in LOADS:
            d = decided.get((case["id"], load))
            answer = d if d is not None and d["decision"] == "label wrong" else by_id[case["id"]][load]
            check_answer(answer, (case["id"], load))
            decision = {None: "AGREED", "label wrong": "LABEL_WRONG", "open": "OPEN"}[d and d["decision"]]
            verdict, hang_out, bring_in = answer_of(answer)
            out.append({"id": case["id"], "load": load, "verdict": verdict, "hangOut": hang_out,
                        "bringIn": bring_in, "decision": decision})
    return out


def answer_of(answer):
    return answer.get("verdict"), answer.get("hangOut"), answer.get("bringIn")


def check_answer(answer, key):
    verdict, hang_out, bring_in = answer_of(answer)
    if verdict not in VERDICTS:
        raise ValueError(f"no verdict for {key}")
    timed = verdict != "DONT_BOTHER"
    if (hang_out is not None, bring_in is not None) != (timed, timed):
        raise ValueError(f"times don't fit the verdict for {key}")
    for t in (hang_out, bring_in):
        if t is not None and not re.fullmatch(r"([01][0-9]|2[0-3]):[0-5][0-9]", t):
            raise ValueError(f"not an HH:MM time for {key}: {t!r}")


def check_document(decisions, text):
    """The Decision column of docs/reference-set.md must say what decisions.json does, row for row."""
    words = {"**Label wrong.**": "label wrong", "**Open.**": "open"}
    in_doc = {}
    for match in re.finditer(r"^\| `([^`]+)` \| (light|heavy) \|.*\| (\*\*(?:Label wrong|Open)\.\*\*)[^|]*\|$", text, re.M):
        in_doc[(match[1], match[2])] = words[match[3]]
    in_json = {(d["id"], d["load"]): d["decision"] for d in decisions["decisions"]}
    if in_doc != in_json:
        raise ValueError(f"{DOCUMENT} and decisions.json disagree: {sorted(set(in_doc.items()) ^ set(in_json.items()))}")


def kotlin_expectations_file(rows):
    def time(t):
        return "null" if t is None else kotlin_string(t)
    lines = [
        "// Generated by scripts/fetch-reference-forecasts.py from testdata/reference/labels.json and",
        "// decisions.json. Don't edit: see docs/reference-set.md.",
        "@file:Suppress(\"ktlint\", \"detekt:all\")",
        "",
        "package io.github.dryingday.reference",
        "",
        "import io.github.dryingday.engine.Load",
        "import io.github.dryingday.engine.Verdict",
        "",
        "/** What ReferenceSetTest expects of every case and load, in the order of cases.json. */",
        "internal fun referenceExpectations(): List<ReferenceExpectation> = listOf(",
    ]
    for r in rows:
        lines.append(f"    ReferenceExpectation({kotlin_string(r['id'])}, Load.{r['load'].upper()}, Verdict.{r['verdict']}, "
                     f"{time(r['hangOut'])}, {time(r['bringIn'])}, Decision.{r['decision']}),")
    lines.append(")")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--offline", action="store_true", help="generate from the saved responses only")
    args = parser.parse_args()
    cases = json.loads(CASES.read_text())["cases"]
    paths = {case["id"]: TESTDATA / f"{case['id']}.json" for case in cases}
    if args.offline:
        responses = {i: json.loads(path.read_text()) for i, path in paths.items()}
    else:
        responses = {}
        for case in cases:
            print(f"fetching {case['id']}", file=sys.stderr)
            responses[case["id"]] = fetch(case)
    # Generate and check before writing anything, so a failed fetch, a bad response or a bad decision leaves
    # the files as they were.
    kotlin = kotlin_file(cases, responses)
    decisions = json.loads(DECISIONS.read_text())
    check_document(decisions, DOCUMENT.read_text())
    expectations_kotlin = kotlin_expectations_file(expectations(cases, json.loads(LABELS.read_text()), decisions))
    if not args.offline:
        for i, data in responses.items():
            paths[i].write_text(json.dumps(data, separators=(",", ":")) + "\n")
    KOTLIN.write_text(kotlin)
    KOTLIN_EXPECTATIONS.write_text(expectations_kotlin)
    print(f"wrote {KOTLIN} and {KOTLIN_EXPECTATIONS} for {len(cases)} cases", file=sys.stderr)


if __name__ == "__main__":
    main()
