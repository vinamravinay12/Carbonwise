# CarbonWise

> *Get wise to your carbon footprint, one sentence a day.*

An Android app that turns a single plain-language sentence about your day —
*"Drove 15 km to work, had a chicken thali for lunch, ran the AC for 6 hours"* —
into a transparent, trustworthy carbon-footprint breakdown, a 7-day trend, and the
single most effective change you could make.

## The core principle

**The AI interprets language. A deterministic engine does all the math.**

- The parser's *only* job is messy sentence → structured activities
  (`"chicken thali"` → `meal_chicken, 1 serving`).
- A fixed, transparent [emission-factor table](app/src/main/java/com/rivi/carbonwise/domain/EmissionFactors.kt)
  and a pure [calculation engine](app/src/main/java/com/rivi/carbonwise/domain/CarbonEngine.kt)
  turn that into kilograms of CO₂.
- The AI never invents, estimates, or outputs a carbon number. Every figure traces
  back to a known factor, shown next to each activity.

## Features

| Feature | Where |
|---|---|
| Natural-language day logging | `ui/screens/HomeScreen.kt` |
| Smart activity parsing (Gemini + rule-based fallback) | `parser/` |
| Transparent per-activity & per-category breakdown | `ui/screens/DayDetail.kt` |
| Smarter AI swap: AI picks the best change, engine prices it (today / per-year / trees) | `advisor/`, `CarbonEngine.computeSwapFor` |
| History + 7-day trend | `ui/screens/HistoryScreen.kt`, `TrendsScreen.kt` |
| Benchmarking (target & average) | `domain/Benchmarks.kt` |
| Friendly insight phrasing | `domain/InsightPhraser.kt` |
| Auto-tracking (Activity Recognition) — detect trips, confirm mode + distance | `recognition/` |

## Architecture

Single-module MVVM, Kotlin, Jetpack Compose (Material 3).

```
sentence ─▶ ActivityParser ─▶ CarbonEngine ─▶ InsightPhraser ─▶ Room ─▶ UI
            (AI / rules)       (pure math)     (phrasing)        (local)
```

- **Parsing** sits behind the `ActivityParser` interface with two implementations:
  - `GeminiParser` — real `gemini-2.5-flash`, strict JSON schema, forbidden by prompt
    from producing carbon numbers.
  - `RuleBasedParser` — deterministic keyword/quantity parser; the offline-safe
    fallback used for tests and when no API key is configured.
  - `FallbackParser` wraps them so a failure or empty AI result degrades gracefully.
- **Smarter swaps** sit behind the `SwapAdvisor` interface. `GeminiSwapAdvisor` chooses
  the single most impactful *and* realistic change for the day (a short car trip is easier
  to swap than a flight) and returns only activity-type names — never numbers. The engine
  then prices it deterministically: saving today, projected over a year, and the
  trees-per-year that saving equates to. No key / a failed call falls back to the rule-based
  swap, so the insight is always present.
- **Engine** (`CarbonEngine`) is pure Kotlin — no Android, network, or AI — and fully
  unit-tested.
- **Auto-tracking** (`recognition/`) uses the Google Play Services **Activity Recognition
  Transition API** to detect when the user starts/stops being `IN_VEHICLE`, `ON_BICYCLE`,
  `WALKING`, or `RUNNING`. On a detected trip, a **foreground GPS service**
  (`TripLocationService`) measures distance + a movement signature (avg/max speed, stops,
  GPS dropouts). When the trip ends:
  - For a **vehicle**, the signature is classified (`VehicleModeClassifier` — a transparent
    heuristic with a *car prior*, optionally refined by Gemini) to pre-select the likely
    mode, and a notification asks **"Were you just in a car? — Yes / Something else."**
    "Yes" logs car + GPS distance in one tap; "Something else" opens the picker
    (bus/metro/train/tram/ferry/auto/two-wheeler).
  - For **active travel**, it logs the distance and credits the emissions avoided.

  The API gives *kind*; GPS gives *distance*; the user confirms the *mode* — so no carbon
  number is ever guessed. It degrades gracefully: no GPS/permission → distance falls back to
  a duration estimate or manual entry, and manual sentence-logging is always available.
  (Note: automatic background GPS is constrained by Android's foreground-service rules and
  needs on-device validation.)
- **Storage** is Room, local only. No accounts, no cloud, no sync.

## Running it

1. Open in Android Studio and run the `app` configuration on a device/emulator
   (minSdk 26).

2. **From the CLI**, build with the JDK that ships with Android Studio (Gradle 8.13
   does not support JDK 24+):

   ```bash
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew :app:assembleDebug
   ./gradlew :app:testDebugUnitTest
   ```

### Enabling Gemini (optional)

The app works fully without an API key (rule-based parser). To use Gemini, add to
`local.properties` (git-ignored):

```properties
GEMINI_API_KEY=your_key_here
```

The Home screen shows an **On-device** / **Gemini** badge reflecting which is active.

## Tests

`app/src/test/` covers the trust-critical core:

- `CarbonEngineTest` — factor math, cross-category aggregation, unknown-type safety,
  best-swap selection, determinism.
- `RuleBasedParserTest` — canonical sentence, safe defaults, unrecognized fragments,
  most-specific-keyword matching.

## Scope (intentionally out)

No accounts/login/cloud sync, no gamification, no receipt/bill scanning, no social
features, no multi-region database. A focused, private, single-purpose personal tool.

> The original spec excluded automatic activity detection; auto-tracking via Activity
> Recognition was added deliberately as a later iteration to make tracking effortless,
> while preserving the "no guessed numbers" principle (the user confirms mode + distance).

> Emission factors are illustrative and oriented to India — reasonable but not
> formally audited, kept in one configurable table.
