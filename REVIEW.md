# Review instructions

Drying Day tells people whether their washing will dry outside. A wrong verdict or a wrong bring-in time is the bug that matters most; a privacy leak is the one that matters second.

## What Important means here

Reserve Important for findings that would:

- Give a wrong verdict, hang-out or bring-in time for the forecast it was given (thresholds misapplied, off-by-one hours, time zone or daylight-saving mistakes, sunset taken from the wrong day)
- Crash, hang, or show stale data as current
- Send data off the device beyond the request to the user's chosen weather source, or log coordinates
- Request a permission beyond coarse location, or use location more precisely than needed
- Break F-Droid compatibility: a non-free dependency, Google Play Services, Firebase, a proprietary build step, or a library from outside Maven Central and Google Maven (Gradle plugins may also come from the Gradle Plugin Portal, which F-Droid's scanner allows)
- Put personal data in the repository: a real home location, addresses, names

Style, naming and refactoring suggestions are Nit at most.

## Cap the nits

Report at most three Nits per review.

## Do not report

- Anything CI already enforces: ktlint, detekt, compiler warnings
- `gradle/wrapper/**`, generated code, lockfiles
- Recorded forecast fixtures under `**/testdata/**`, except personal locations in them
- Store screenshots under `fastlane/**/images/**`

## Always check

- Changes to verdict logic come with tests, and threshold changes are reflected in `docs/PLAN.md` or the bolt spec
- Shared code in `commonMain` doesn't use Android or iOS APIs
- Times are computed in the forecast location's time zone with kotlinx-datetime, never the device's default zone
- Network calls use HTTPS, have timeouts, and handle offline and error responses
- User-facing text is in string resources, not hard-coded
- No default or hard-coded location anywhere
- The Open-Meteo attribution stays visible wherever its data is shown

## Evidence

Every finding cites `file:line`. Claims about behaviour need a citation in the source or a failing test, not an inference from a name.
