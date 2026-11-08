# Reference set: labels and disagreements

How the drying rules compare with independent judgement, case by case. The process is in
[docs/bolts/02-drying-rules.md](bolts/02-drying-rules.md) ("Reference set"); the cases and their
forecasts are in [`shared/src/commonTest/testdata/reference/`](../shared/src/commonTest/testdata/reference/).

## Blind labels

`labels.json` in that directory is the labeller's answer, unedited: for each of the 37 cases and both
loads, a verdict, hang-out and bring-in to the hour, and a reason.

The labeller was a fresh Claude subagent, run on 8 November 2026, before the engine existed in `main`.
It was given one file: each case's local date, zone, sunrise and sunset, the time it's asked at, and the
hourly forecast for the day in local time, as the generator maps it (no case notes). It was told to read
nothing else, in particular not this repository, and not to use the web. Its instructions:

> You are an experienced laundry-drying advisor. Someone is deciding whether to hang their washing on an
> outdoor line. [...] Give your own expert judgement from the weather data and your knowledge of how
> washing dries. [...] For each case, and for each of two loads: "light": shirts, underwear, thin cotton,
> synthetics; "heavy": towels, jeans, bedding, hoodies (a mixed load counts as heavy) [...] "WILL_DRY":
> hung out at the right time, it will be fully dry on the line that day; "PARTLY_DRY": worth hanging out,
> but it will need finishing indoors (it gets at least about half dry); "DONT_BOTHER": not worth putting
> out; dry indoors. [...] Washing can't go out before the asked-at time or before sunrise, and must come in
> before rain, dew/fog or dark.

The labels are one model's judgement, not ground truth: evidence for a rule change, never the decision.

## Comparison

"Times" counts agreed verdicts with hang-out and bring-in both within an hour of the label (and every
agreed Don't bother). The bring-in error is over agreed Will dry and Partly dry verdicts.

| Rules | Verdicts agree | Times within 1 h | Mean bring-in error | Mean bias (engine − label) |
|---|---|---|---|---|
| As first specified (PR 3a, #18) | 69 of 74 | 54 | 48 min | +15 min |
| With the two higher VPD bands | 69 of 74 | 55 | 43 min | +3 min |

**The VPD bands.** With the VPD table flat at 2 points from 1.0 kPa, every hot, dry day's bring-in came
later than the label, by up to two hours: the hottest case peaks at 5.9 kPa and scored the same as
20 °C at 57 %. Adding 2.5 points from 1.5 kPa and 3 from 2.5 kPa brought 19 case-loads earlier and most of
the hot days within the hour. Also tried, on a Python copy of the engine that matched its output on all
74 case-loads, and not adopted:

- a 3.5-point band from 4.0 kPa: no outcome changed;
- a gentler wind factor (1.2 from 15 km/h, 1.3 from 30): one more time agreed, at the cost of a verdict
  elsewhere;
- a light need of 5 or a heavy need of 14 to 16: moved the bias between loads without lowering the error.

## Disagreements with the current rules

For the maintainer to decide, one by one: the label was wrong, or the rule was (a rule change being its
own high-tier PR). Engine times are local; dryness is the engine's.

| Case | Load | Differs in | Engine | Label | Labeller's reason | Decision |
|---|---|---|---|---|---|---|
| `dublin-2025-08-18-0800` | heavy | verdict | Will dry 100 %, 08:00–17:54 | Partly dry, 08:00–18:00 | Humidity stays around 75-80%, so heavy items won't fully dry. | |
| `edinburgh-2025-03-30-0800` | heavy | time | Will dry 100 %, 08:00–14:45 | Will dry, 08:00–16:00 | Windy and dry all day gives heavy items enough time. | |
| `greenwich-2025-03-30-0800` | heavy | time | Will dry 100 %, 08:00–14:09 | Will dry, 08:00–16:00 | Brisk wind and very dry afternoon air finish heavy items before evening. | |
| `greenwich-2025-05-25-0800` | heavy | time | Will dry 100 %, 11:00–14:55 | Will dry, 11:00–17:00 | Strong wind and humidity in the 30s dry heavy items through the afternoon. | |
| `greenwich-2025-10-26-0800` | light | time | Will dry 100 %, 08:00–14:13 | Will dry, 08:00–13:00 | Cool but breezy and dry until rain arrives late afternoon. | |
| `madrid-2025-01-04-0800` | light | verdict | Don't bother 10 % | Partly dry, 12:00–17:00 | Fog clears late and humidity stays in the 80s with no wind; light items only part dry. | |
| `madrid-2025-02-16-0800` | light | time | Will dry 100 %, 10:00–14:53 | Will dry, 09:00–13:00 | Cold start but sunny with humidity falling to the 30s by afternoon. | |
| `madrid-2025-07-17-0800` | light | time | Will dry 100 %, 08:00–11:17 | Will dry, 08:00–10:00 | Intense heat and very dry air dry light things almost at once. | |
| `oslo-2025-06-21-0800` | heavy | time | Will dry 100 %, 08:00–12:40 | Will dry, 08:00–14:00 | Long sunny day with low humidity dries heavy items by early afternoon. | |
| `rome-2025-07-09-0800` | heavy | time | Will dry 100 %, 08:00–11:53 | Will dry, 08:00–13:00 | Hot, dry and breezy all day dries heavy items by early afternoon. | |
| `rome-2025-10-25-0800` | heavy | verdict | Will dry 100 %, 08:00–14:38 | Partly dry, 08:00–15:00 | Rain arrives mid-afternoon before heavy items can finish. | |
| `seattle-2025-03-09-0800` | heavy | time | Partly dry 55 %, 10:00–15:00 | Partly dry, 08:00–14:00 | Cool, and rain arrives mid-afternoon before heavy items dry. | |
| `seattle-2025-03-09-0800` | light | time | Will dry 100 %, 10:00–14:36 | Will dry, 08:00–13:00 | Cool but windy with humidity falling to the 60s before rain. | |
| `sydney-2025-06-26-0800` | heavy | time | Will dry 100 %, 08:00–14:42 | Will dry, 08:00–16:00 | Breezy with humidity in the 40s; heavy items just make it before sunset. | |
| `sydney-2025-10-05-0800` | light | time | Will dry 100 %, 08:00–12:04 | Will dry, 08:00–11:00 | Humid start soon gives way to warm sun and dry air. | |
| `tromso-2025-07-18-0800` | heavy | time | Will dry 100 %, 08:00–12:15 | Will dry, 08:00–14:00 | Warm, dry and breezy all day with strong sun. | |
| `wellington-2025-04-06-0800` | heavy | verdict | Don't bother 34 % | Partly dry, 08:00–12:00 | Showers from midday cut drying short for heavy items. | |
| `wellington-2025-04-06-0800` | light | verdict | Partly dry 69 %, 08:00–11:00 | Will dry, 08:00–11:00 | Strong wind dries light things before showers build from late morning. | |
| `wellington-2025-12-28-0800` | heavy | time | Partly dry 83 %, 14:00–20:56 | Partly dry, 15:00–19:00 | Only a few dry hours after the showers before evening humidity rises. | |
