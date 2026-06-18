# CarbonWise

> *Get wise to your carbon footprint, one sentence a day.*

**Repository:** https://github.com/vinamravinay12/Carbonwise

CarbonWise is an Android app that helps an everyday person **understand, track, and
reduce** their personal carbon footprint with the least possible friction. You describe
your day in plain language — *"Drove 15 km to work, had a chicken thali for lunch, ran
the AC for 6 hours"* — and the app turns it into a transparent footprint breakdown, a
trend over time, an impact summary, and the single most effective change you could make.
It can also detect your trips automatically, answer carbon questions in a chat (text or
photo), and compare options like *"petrol car vs metro."*

---

## 1. Chosen vertical

**Sustainability / Climate — personal carbon-footprint management.**

The brief: *design a solution that helps individuals understand, track, and reduce their
carbon footprint through simple actions and personalized insights.* CarbonWise targets
the everyday individual who knows they *should* track their footprint but finds existing
tools too tedious to keep using. The whole product is built around removing friction
while keeping every number trustworthy.

## 2. Approach and logic

The product rests on one principle that shapes the entire architecture:

> **The AI interprets language and images. A deterministic engine does the math.**

- The AI's job is to turn a messy human sentence (or photo) into **structured activities**
  — *what* was done and *how much* (`"chicken thali"` → `meal_chicken, 1 serving`).
- A fixed, transparent **emission-factor table** and a **pure calculation engine** turn
  that structured data into kilograms of CO₂.
- For everyday activities the engine's audited factors are used, so the **same activity
  always yields the same number** and every figure is traceable to a visible factor.

This keeps the numbers **accurate, consistent, and explainable**, and puts the AI on the
task it is genuinely good at — understanding language — instead of guessing quantities.

Where the deterministic table can't help (a specific car model, a niche dish), the app
**degrades gracefully**: the AI supplies an *estimated* factor, and the result is clearly
**badged "AI estimate"** so the user always knows which numbers are audited and which are
approximate. Exploratory features (the *Ask AI* chat and comparisons) are explicitly
AI-estimated and labelled as such; **logged history stays on the deterministic engine.**

## 3. How the solution works

```
 sentence / photo ─▶ ActivityParser ─▶ CarbonEngine ─▶ InsightPhraser ─▶ Room ─▶ UI
                     (Gemini / rules)   (pure math)     (phrasing)        (local)
```

**Three ways to track**

1. **Type a sentence** (Today tab). The parser extracts activities; the engine computes
   the footprint, per-category breakdown, the single best swap, and an impact summary.
2. **Auto-tracking** (`recognition/`). The Google Play Services **Activity Recognition
   Transition API** detects when you start/stop a vehicle, bicycle, walk, or run. A
   foreground **GPS service** measures the trip distance and a movement signature
   (avg/max speed, stop count, GPS dropouts). When the trip ends, a notification asks
   *"Were you just in a car? — Yes / Something else,"* the engine prices it, and active
   travel (walking/cycling) is credited as **emissions avoided**.
3. **Ask AI** (chat). A conversational assistant (with memory) answers carbon questions,
   compares options on a fair common basis, and accepts a **photo** ("what's the carbon
   impact of this?") via Gemini vision.

**Layers**

- **Parsing** sits behind the `ActivityParser` interface: `GeminiParser` (open-ended,
  maps to known factors and estimates the rest) with a deterministic `RuleBasedParser`
  fallback, wrapped by `FallbackParser` so a failed/empty AI call degrades gracefully.
- **Engine** (`CarbonEngine`) is **pure Kotlin** — no Android, network, or AI — fully
  unit-tested. It computes the footprint, the avoided/net figures, the best swap (today +
  per-year + trees-equivalent), and comparisons on a fair basis.
- **Assistant** (`advisor/`, `InsightPhraser`) phrases numbers the engine produced into
  warm, honest guidance, and picks the *smartest* swap — but the kilograms are always the
  engine's.
- **AI transport** (`ai/GeminiClient`) is a small direct REST client to the Gemini
  `v1beta` API (supports system instructions, JSON mode, multi-turn chat, and inline
  images).
- **Storage** is **Room, local only** — no accounts, no cloud, no sync.
- **UI** is Jetpack Compose (Material 3), MVVM with `HomeViewModel` / `HistoryViewModel`.

## 4. Running it

1. Open in Android Studio and run the `app` configuration (minSdk 26).
2. From the CLI — build with the JDK bundled in Android Studio (Gradle 8.13 doesn't
   support JDK 24+):

   ```bash
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew :app:assembleDebug
   ./gradlew :app:testDebugUnitTest
   ```

**Enabling the AI features** — the app runs fully without a key (on-device rule-based
parser + heuristics). For the conversational/vision/open-ended features, add a Gemini key
to `local.properties` (git-ignored, never committed):

```properties
GEMINI_API_KEY=your_key_here
```

The Today header shows an **On-device** / **Gemini** badge for the active mode.

## 5. Assumptions

- **Emission factors are illustrative** and oriented to India — reasonable but not
  formally audited — kept in one configurable table (`EmissionFactors.kt`).
- Footprints are **estimated per activity** from category and quantity, not a full
  lifecycle analysis.
- Input is **self-reported**; for a personal tool this is acceptable, and showing the
  factor next to each activity keeps it honest.
- **Active travel is credited as "avoided" emissions** vs driving the same distance; the
  headline figure is *net* (emitted − avoided), which can go below zero on a green day.
  This is "net vs business-as-usual," not literal negative emissions, and is labelled so.
- The AI returns valid structured data; this is safeguarded by a strict response schema,
  validation, and the deterministic fallback.

---

## 6. Evaluation focus areas

### Code Quality — structure, readability, maintainability
- **Clean MVVM, single module**, layered by responsibility: `domain/` (pure logic),
  `data/` (Room + repository), `parser/` · `advisor/` · `recognition/` (AI & sensors
  behind interfaces), `ui/` (Compose).
- **Dependency-inversion via interfaces** (`ActivityParser`, `SwapAdvisor`,
  `VehicleModeClassifier`) so AI implementations are swappable and testable, each with a
  deterministic fallback.
- **No God-objects**; small, documented functions; shared helpers (e.g. one
  `formatAmount`) instead of duplication.
- **Android Lint: 0 errors, 0 code-smell warnings** (remaining notices are
  dependency-version suggestions only).

### Security — safe and responsible implementation
- **Privacy-first:** all data is stored **locally** (Room); no accounts, no analytics, no
  cloud sync.
- The **API key is never committed** — read from git-ignored `local.properties` into
  `BuildConfig` at build time.
- **Least-privilege sensors:** location/activity-recognition are optional, requested at
  point of use, and the app fully functions if denied. **No microphone, no background
  audio.**
- The AI is constrained by **strict response schemas + validation**, and is explicitly
  instructed not to fabricate personal-health claims.

### Efficiency — optimal use of resources
- The **calculation engine is pure and O(n)** over the day's activities.
- The two network-bound steps on logging (swap + impact) run **in parallel**, and
  zero-emission trips **skip** the swap call entirely.
- **Images are downscaled** (~1024 px JPEG) before upload; chat history is kept
  **text-only** so image bytes aren't re-sent each turn.
- Foreground GPS runs **only during a detected trip**; Activity-Recognition transitions
  are **push-based** (no polling).

### Testing — validation of functionality
- **43 JVM unit tests** over the trust-critical, deterministic core
  (`./gradlew :app:testDebugUnitTest`):
  - `CarbonEngineTest` — factor math, aggregation, custom/AI factors, avoided & net,
    best-swap, fair-basis comparison, determinism.
  - `EmissionFactorsTest` — table integrity (every swap is a known, lower-carbon,
    same-unit substitute).
  - `RuleBasedParserTest`, `VehicleModeClassifierTest`, `ImpactNotesTest`,
    `InsightPhraserTest`, `BenchmarksTest`, `DetectedKindTest`.
- AI and sensor code sit behind interfaces with deterministic fallbacks, so the core is
  testable without a network or device.

### Accessibility — inclusive and usable design
- **Plain-language input** is the primary interaction — no forms, dropdowns, or jargon.
- Material 3 with **scalable typography**, adequate **touch targets**, and a high-contrast
  palette in **light and dark themes**.
- Meaningful **content descriptions** on icons/controls and image attachments for screen
  readers.
- Insights are phrased in **clear, encouraging language**, and every number is shown with
  the factor behind it so it's understandable, not a black box.

---

## Scope (intentionally out)
No accounts/login/cloud sync, no gamification, no receipt/bill scanning, no social
features, no multi-region database — a focused, private, single-purpose personal tool.
