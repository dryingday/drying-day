# Reference forecasts

Real past forecasts for checking the drying rules, as described in
[docs/bolts/02-drying-rules.md](../../../../../docs/bolts/02-drying-rules.md) ("Reference set").

**Weather data by [Open-Meteo.com](https://open-meteo.com/)**, from its historical-forecast API, under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The responses are saved here with only two
changes: the coordinates Open-Meteo echoes back are rounded to two decimal places, and the
`generationtime_ms` field is dropped.

- `cases.json`: the case list. Each case is a public landmark (no homes or personal places, coordinates
  to two decimals), a date, the local time the engine is asked at, and a note on why the case was chosen.
- `<case id>.json`: Open-Meteo's response for that case, from the day before to three days after.
- `labels.json`: the blind labels, unedited. How they were made, and how the engine compares, is in
  [docs/reference-set.md](../../../../../docs/reference-set.md).
- `ReferenceForecasts.kt` in `commonTest` is generated from these files. Don't edit it.

To change the set, edit `cases.json`, then run from the repository root:

```
python3 scripts/fetch-reference-forecasts.py            # fetch, then generate
python3 scripts/fetch-reference-forecasts.py --offline  # regenerate from the saved responses
python3 -m unittest discover -s scripts                 # tests for the script
```
