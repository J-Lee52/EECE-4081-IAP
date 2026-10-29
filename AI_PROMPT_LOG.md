# AI Prompt-and-Diff Log

## Milestone 1 — GitHub Copilot Verification

**AI tool:** GitHub Copilot Chat  
**Environment:** Android Studio

### Prompt #1

> Explain what the onCreate method in this starter Android project does.

### Result

GitHub Copilot inspected the Android project and explained the `onCreate` method in `MainActivity.kt`, including its use of `super.onCreate(savedInstanceState)`, `enableEdgeToEdge()`, and `setContent`.

### Code Changes

No code changes were requested or generated from this prompt.

**Diff:** None.


## GitHub Copilot Chat — Requirements Elicitation
### Prompt #2

>I am developing an Android mobile shot-timer application for recreational and competitive shooters. The application will use a smartphone’s microphone to detect firearm discharges, record shot timestamps and split times, display session results, and allow the ability to save the session results to local storage for future review. 
Elicit the functional and non-functional requirements for this application. For the functional requirements, provide:
user stories in the format “As a [user], I want [goal], so that [reason]”
acceptance criteria for each user story
non-functional requirements stated in measurable or testable terms
questions you would ask the stakeholder to clarify ambiguous or missing requirements.
> Do not write or modify any code.

### Diff
No application source code was modified by GitHub Copilot.

Documentation changes resulting from this interaction:
- Added `AI_ELICI_AUDIT_LOG.md` comparing the AI-generated requirements with my independently written requirements.
- No Copilot-generated requirement was copied directly into `REQUIREMENTS.md` without review.

### Notes
The Copilot response was used for requirements elicitation and audit purposes only.

## GitHub Copilot Chat — First Draft Domain Model
### Prompt #3

Using only the referenced `REQUIREMENTS.md` file, draft a UML class diagram for the domain model of this Android shot-timer application. Identify the domain entities, value objects, important attributes, relationships, and multiplicities that are supported by the requirements. Do not use other project files, assume implementation details, or invent features that are not stated in the requirements. Provide the diagram in Mermaid `classDiagram` source and briefly explain your modeling choices.

### AI Output

The AI generated an initial UML class diagram containing:

- `Session`
- `Shot`
- `SessionSettings`
- `StartDelayMode`
- `SessionState`
- `SessionResults`
- `SessionHistory`

The original AI response was saved separately as `AI_FIRST_DRAFT_DOMAIN_MODEL.md` before any comparison or revision was performed.

### Existing Design Context

Before prompting the AI, I had already developed and refined the architecture and domain direction for the application. That design included:

- `ShootingSession`
- `Shot`
- `TimerSettings`
- `StartDelay`
- `FixedDelay`
- `RandomDelay`
- `SessionStatistics`
- `SessionStatisticsCalculator`

The broader architecture also already assigned timer workflow state and transitions to `TimerEngine`, persistence to `SessionRepository`, and microphone access and shot detection to separate service/platform components.

The AI was intentionally given only `REQUIREMENTS.md` so that its first draft could be compared independently against my existing design rather than being influenced by the architecture I had already established.

### Code Changes

No code changes were requested or generated from this prompt.

### Diff

I compared the AI-generated first draft with the domain model and architecture decisions I had already established.

- **AI:** Used `Session` as the main domain entity.  
  **My design:** Uses `ShootingSession` to make the domain meaning more explicit.

- **AI:** Did not model an explicit session timing origin.  
  **My design:** Includes `ShootingSession.startTime`, because the start beep establishes the timing origin and shot timestamps are measured relative to it.

- **AI:** Used `SessionSettings`.  
  **My design:** Uses `TimerSettings`.

- **AI:** Modeled the start delay using a `StartDelayMode` enumeration together with nullable `fixedDelay`, `minimumDelay`, and `maximumDelay` fields.  
  **My design:** Uses a `StartDelay` value-object family with `FixedDelay(duration)` and `RandomDelay(minDelay, maxDelay)`. This prevents invalid combinations of fixed and random delay values.

- **AI:** Placed `SessionState` directly on `Session`.  
  **My design:** Keeps `TimerState` outside the core domain model. The existing architecture assigns authoritative workflow state and state transitions to `TimerEngine`.

- **AI:** Included `stop()` directly on `Session`.  
  **My design:** Places stop behavior on `TimerEngine`, because stopping controls the runtime transition from `Recording` to `Completed` and stops further shot acceptance.

- **AI:** Included `save()` directly on `Session`.  
  **My design:** Treats saving as a persistence responsibility handled through `SessionRepository`, not by `ShootingSession`.

- **AI:** Added a `SessionHistory` domain class and modeled it as owning saved sessions.  
  **My design:** Does not include `SessionHistory` as a core domain object. Saved sessions are retrieved through persistence behavior, while the history view belongs outside the core domain model.

- **AI:** Used `SessionResults` as a result object associated directly with `Session`.  
  **My design:** Uses `SessionStatistics` as a derived value produced from authoritative `Shot` records rather than as separately authoritative stored state.

- **AI:** Added `splitStatisticsAvailable` as a Boolean field.  
  **My design:** Does not store a separate availability flag because availability can be derived from whether valid split statistics exist.

- **AI:** Did not explicitly model where session-statistics calculations are performed.  
  **My design:** Includes the previously established stateless `SessionStatisticsCalculator`, which calculates session statistics from the recorded `Shot` objects and keeps calculation rules separate from result data.

- **AI:** Correctly modeled one session containing zero or more `Shot` records.  
  **My design:** Retains this relationship.

- **AI:** Correctly modeled each shot with a sequential shot number, timestamp, and optional split time.  
  **My design:** Retains these concepts and represents split time as a derived optional value.

- **AI:** Correctly avoided UI, Android microphone APIs, database implementation classes, and other platform-specific classes in the core domain model.  
  **My design:** Uses the same domain boundary.

### Result

The final domain model reflects the architecture and domain decisions that had already been established before the AI draft was generated.

The final model contains:

- `ShootingSession`
- `Shot`
- `TimerSettings`
- `StartDelay`
- `FixedDelay`
- `RandomDelay`
- `SessionStatistics`
- `SessionStatisticsCalculator`

The AI first draft was preserved unchanged so that the differences between the AI-generated model and my independently developed design could be evaluated and documented.


## GitHub Copilot — ADR-001 Project-Wide Consistency Review

### Prompt #4

> @project Review ADR-001.md against the rest of this project.
>
> Check whether the responsibilities assigned to AudioInput,
> ShotDetector, TimerViewModel, and TimerEngine are consistent with
> the architecture documentation, requirements, and source code.
>
> Identify any contradictions or unsupported claims.
> Do not rewrite the ADR yet.

### AI Output

GitHub Copilot reviewed `ADR-001.md` against the project requirements, architecture documentation, and current source tree.

Copilot reported that:

- The responsibilities assigned to `AudioInput` are consistent with the planned architecture.
- The responsibilities assigned to `ShotDetector` are consistent with the required microphone-threshold and cooldown behavior.
- The ADR correctly rejects placing microphone and detection responsibilities in `TimerViewModel`.
- The use of `TimerEngine` as the workflow/state coordinator is consistent with the architecture documentation.
- The current source tree does not yet contain implementations of `AudioInput`, `ShotDetector`, `TimerViewModel`, or `TimerEngine`.
- The ADR therefore describes architectural intent that is documented in the project but is not yet reflected in the current implementation.

### Review of AI Feedback

I agreed with Copilot's assessment that the component responsibilities in ADR-001 are consistent with the architecture documentation.

I also clarified one distinction in the feedback: the requirements define the behavior the application must provide, such as microphone monitoring, threshold-based detection, cooldown enforcement, and timer states. The requirements do not require specific classes named `AudioInput`, `ShotDetector`, or `TimerEngine`. Those component names and responsibility boundaries come from my architecture design.

Copilot also noted that the current source code does not yet implement these architecture components. This does not contradict ADR-001 because the ADR records an architectural decision that has already been made for the planned implementation. The corresponding source code has not yet been implemented and committed to the repository.

For the current M4 milestone, I therefore did not treat the absence of these classes from the source tree as a reason to change the architectural decision. The implementation should later be checked against ADR-001 once these components are added.

### Diff / Changes Made

The Copilot review did not require a change to the selected architectural decision or any other changes.

### Result

The Copilot review confirmed that ADR-001 is consistent with the project's documented architecture.

The source-code comments were noted but do not currently require changes to the ADR because the relevant architecture components have not yet been implemented and committed. Once implementation begins, the source code will need to be checked against the ADR to ensure that AudioInput, ShotDetector, TimerViewModel, and TimerEngine preserve the responsibility boundaries documented in the architecture.

No application source code was changed as a result of this review.


## Claude Chat — Walking Skeleton Planning

### Prompt #5

> Please propose a plan for a walking skeleton with a continuous integration that covers every layer of the architecture.

### AI Output

Claude proposed a thin slice that touches every layer of the architecture:

- Start a session with default settings and a short fixed delay.
- Play the start beep.
- Detect a real shot through `AudioInput` and `ShotDetector`.
- Record the `Shot` in `TimerEngine` and compute statistics with `SessionStatisticsCalculator`.
- Save the session through `SessionRepository` to real storage.
- Close and reopen the app and show the saved session in a history screen.

Claude recommended getting CI green on the unchanged starter project before writing slice code. When I asked, it also explained Room versus file-based storage.

### Code Changes

No code changes were requested or generated from this prompt.

### Diff

None.

### Result

I adopted the CI-first order and the branch-and-pull-request workflow. Storage choice (Room or file-based) was left open here and settled in Prompt #9: file-based JSON.


## Claude Chat — CI Workflow and Slice Class Outline

### Prompt #6

> Please proceed with drafting the GitHub Actions workflow and an outline of the slice's classes

### AI Output

Claude generated a GitHub Actions workflow and a class outline.

The workflow:

- checks out the code and sets up JDK 25, because `gradle/gradle-daemon-jvm.properties` sets `toolchainVersion=25`
- runs `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`
- uploads the unit-test report and the debug APK as artifacts

Claude looked up current action versions before writing the file (`actions/checkout@v6`, `actions/setup-java@v5`, `actions/upload-artifact@v7`). It left out `gradle/actions/setup-gradle` because v6 requires accepting Terms of Use for its caching component.

The class outline was layered (domain, engine, audio, data, ui, app) and listed unit-test targets that CI could run.

Claude could not run the workflow. It only validated the YAML syntax.

### Code Changes

Added `.github/workflows/Continuous_Integration.yml` in commit `5c0e01b`, on branch `walking-skeleton` (pull request #1).

### Diff

I changed two things from Claude's version:

- **File name:** Claude named it `ci.yml`. I renamed it to `Continuous_Integration.yml`.
- **Workflow name:** Claude's first line was `name: CI`. I changed it to `name: Continuous_Integration`.

I made no other changes to the workflow, so that the AI's output could be tested as drafted.

I also decided to do the work on a branch (`walking-skeleton`), push the CI file alone first, and open a pull request into `master`.

### Result

The first pull-request run (CI file only, starter project) passed in about 2 minutes. This showed that the JDK 25, Gradle 9.6, and Android SDK 37 setup works on a GitHub runner. A later run on the same pull request, after the rename and the Kotlin files were added, also passed.


## Claude Chat — Domain Model Implementation and Unit Tests

### Prompt #7

> Please draft the domain model and unit test files in Kotlin. I will review the code before implementation.

### AI Output

Claude drafted four domain source files and three test files:

- `Shot`, `ShootingSession`, `TimerSettings` (with `StartDelay` and `FixedDelay`), `SessionStatistics`, and `SessionStatisticsCalculator`
- `SessionStatisticsCalculatorTest` (4 tests), `ShootingSessionTest` (9 tests), and `TimerSettingsTest` (7 tests), including an NFR 01 check that split times are within 0.01 s of the expected value

Claude flagged four design decisions for my review:

1. `startTimeEpochMillis` is a `Long`, because `java.time` needs API 26 and the app's `minSdk` is 24.
2. `splitTime` is stored on `Shot` (matching my domain diagram) but computed by `ShootingSession.recordShot()`.
3. `detectionThreshold` is a normalized peak amplitude in the range (0, 1], and the default values are placeholders.
4. `RandomDelay` is deferred; only `FixedDelay` exists in the skeleton.

Claude had no Kotlin toolchain available and could not compile or run any of the code. Verification was left to my local run and to CI.

### Code Changes

Added the seven Kotlin files under `app/src/main/.../domain/` and `app/src/test/.../domain/`. They are in commit `99d3ca1`. That commit's message reads "Updated CI workflow" even though it contains the domain files and tests.

### Diff

I committed the domain files and tests exactly as drafted so that the AI's code could be tested as-is. The CI and local results below are for that unmodified code.

After pull request #1 was merged, I made one change to Claude's Kotlin code. In commit `288c05e`, committed directly to `master`, I renamed `ShootingSession.startTimeEpochMillis` to `startTime` in `ShootingSession.kt` and `ShootingSessionTest.kt` (5 lines), to better match my domain model diagram. The type is still `Long`, so flagged decision 1 is unchanged apart from the name. The first CI run that covered the rename was the pull request #2 run (Prompt #8), which passed.

Apart from this rename, I accepted the four flagged design decisions as drafted.

### Result

Local run in Android Studio: 20 of 20 tests passed

CI run on the pull request: the build and test steps passed. The downloaded `unit-test-report` artifact shows 21 tests, 0 failures, 0 skipped, and 100% successful, in 0.548 s under Gradle 9.6.0 (4 + 9 + 7 new tests plus the starter `ExampleUnitTest`).

Pull request #1 (`walking-skeleton`, containing commits `5c0e01b` and `99d3ca1`) was merged into `master` with a merge commit (`f83b0c8`), so the commit hashes in this log stay valid.

ADR-001 says to check the implementation against its `AudioInput` and `ShotDetector` boundaries once those classes exist. They do not exist yet, so that check is still pending.


## Copilot Chat — Shot Detection and Timer Workflow

### Prompt #8

> Let's move onto the next part of the walking-skeleton.
> Please draft Kotlin files for the audio and engine layers.
> Include tests for the audio and engine layers

### AI Output

Copilot Chat drafted the audio and engine layers and the tests for them, nine files in all:

- `AudioInput` and `BeepPlayer`: interfaces for the microphone and the start beep.
- `ShotDetector`: marks a buffer as a shot when its peak amplitude reaches the threshold, and ignores further loud buffers during a cooldown.
- `Clock` and `Scheduler`: interfaces for time and for delayed actions.
- `TimerState` and `TimerSnapshot`: the five workflow states and the view of them for the UI.
- `TimerEngine`: coordinates the start delay, the beep, shot detection, and `ShootingSession`, and allows only valid state changes.
- `EngineFakes`: test doubles for the four interfaces.
- `ShotDetectorTest` (15 tests) and `TimerEngineTest` (20 tests).

Design points Copilot described: timing is measured from the start of the beep; audio during the delay and the beep is ignored so the beep cannot count as a shot; the engine is synchronized because audio arrives on a background thread; stopping is allowed only while recording.

### Code Changes

Added nine Kotlin files under the `audio` and `engine` packages of `app/src/main/` and `app/src/test/`. They are in commit `943d459` on branch `slice-engine-detector`.

### Diff

I made no modifications to Copilot's code. I only placed the files in the `audio` and `engine` package folders; their contents are unchanged.

AI mistakes found during review: none.

### Result

Local run in Android Studio: 56 of 56 tests passed (21 earlier plus 35 new).

CI run on the pull request: build and test passed. The `unit-test-report` artifact shows 56 tests, 0 failures, 0 skipped. Screenshot: `CI test Passed 002`.

Pull request #2 was merged into `master` with a merge commit (`7070f40`).
ADR-001 calls for `AudioInput` and `ShotDetector` to be separate and for `TimerEngine` to coordinate them. This step implements that, so the check I deferred in Prompt #7 can now be made against real classes.


## Claude Chat — Real Android Adapters and Session Storage

### Prompt #9

> Let's move onto the next phase of the walking skeleton.
> Please draft Kotlin files for the storage layer.

### AI Output

Claude explained the remaining work and changed its earlier lean on storage. It had first leaned toward Room. It now recommended file-based JSON, because Room needs the KSP plugin and Claude could not check that combination against AGP 9.4.1 and Kotlin 2.2.10. It kept storage behind a `SessionRepository` interface so Room could replace it later. It split the work into two pull requests.

**Step 4a, real adapters (no new dependencies):**

- `AndroidAudioInput`: `AudioRecord`, 16-bit mono at 44.1 kHz, 10 ms buffers on a background thread. Its `stop()` does not wait for the audio thread, to avoid a deadlock with the engine's lock.
- `AndroidBeepPlayer`: `ToneGenerator`, 300 ms.
- `AndroidClock`: `SystemClock.elapsedRealtimeNanos()` for timing and `System.currentTimeMillis()` for the start time.
- `MainThreadScheduler`: `Handler.postDelayed` on the main thread.
- The `RECORD_AUDIO` permission line for the manifest.

Claude said these classes need real Android APIs, so JVM unit tests cannot cover them, and that they can only be proven on a device.

**Step 4b, storage:**

- `SessionRepository`, `SessionJson` (durations stored as nanoseconds; splits recomputed on load), and `FileSessionRepository` (one JSON file per session, written through a temporary file, corrupt files skipped).
- `SessionJsonTest` (7 tests) and `FileSessionRepositoryTest` (8 tests).
- A `testImplementation` line for `org.json:json:20240303`, because Android's `org.json` is a stub on the JVM. Claude could not check from its workspace that this version exists.

### Code Changes

4a: Four new Kotlin files and one line in `AndroidManifest.xml`. Commit `58d73df` on branch `slice-platform-adapters`, merged as pull request #3 (`6ce5a00`).

4b: Five new Kotlin files plus edits to `gradle/libs.versions.toml` and `app/build.gradle.kts`. Commit `ed93c7f` on branch `slice-storage`, merged as pull request #4 (`d69bc93`).

### Diff

I made no modifications to Claude's code. For the manifest and Gradle edits, I added exactly the lines Claude supplied.


### Result

**4a:** local run 56 of 56 tests passed (no new tests, as expected). CI report: 56 tests, 0 failures, screenshot `CI test Passed 003`.

**4b:** local run 71 of 71 tests passed (56 plus 15 new). CI report: 71 tests, 0 failures, 0 skipped, screenshot `CI test Passed 004`. The `org.json` version resolved without a problem.
The storage decision left open in Prompt #5 is now settled: **file-based JSON**. The save path is covered on the JVM by a test that opens a second repository on the same folder. Closing and reopening the real app is covered by the device run in Prompt #10.


## Claude Chat — ViewModel, Timer Screen, and Device Test

### Prompt #10

> Please draft Kotlin files for the UI and device tests.

### AI Output

Claude answered the two scope questions before writing code.

- `TimerViewModel`: holds the engine's current state, the list of saved sessions, and any error message. It moves engine updates to the main thread, saves a completed session on a background thread, and does not save a session with no shots.
- `TimerViewModelFactory`: builds it with the real adapters and file storage.
- `TimerViewModelTest` (11 tests, using the fakes from Prompt #9 and an in-memory repository).
- A `lifecycle-viewmodel-compose` dependency line.
- `TimerScreen`: Start and Stop, the microphone permission request, live shots with splits, statistics, and a "Past sessions" list. No settings screen and no random delay.
- `DurationFormat` (times to 0.01 s, cut off rather than rounded) and `DurationFormatTest` (4 tests).
- A replacement `MainActivity.kt` that shows `TimerScreen`.

Claude noted one known limitation: the engine cannot be cancelled during the start delay, so closing the app then leaves the microphone open until the process ends.

### Code Changes

Seven new Kotlin files, a replacement `MainActivity.kt`, and edits to `gradle/libs.versions.toml` and `app/build.gradle.kts`. Commit `03904ec` on branch `slice-viewmodel`, merged as pull request #5 (`f4b2053`).

### Diff

I made no modifications to Claude's code. `MainActivity.kt` was replaced with Claude's version.

AI mistakes found during review: none in the code.

### Result

Local run in Android Studio: 86 of 86 tests passed.

CI run on the pull request: build and test passed. The report shows 86 tests, 0 failures, 0 skipped. Screenshot: `CI test Passed 005`. Pull request was merged successfully.

**Device test:** I ran the app from Android Studio on a physical phone. I tapped Start, granted the microphone permission, heard the beep, clapped my hands to replicate shots, saw detected shots with splits, tapped Stop, saw the session under "Past sessions", then closed and reopened the app and saw the sessions still saved to the phone.

Known limitations and deferred work: the start delay cannot be cancelled; a settings screen (US01) and `RandomDelay` are not built; a session with no shots is not saved (my choice, kept as drafted).


## Claude Code — M6 Tests, Coverage and Bug Injection

### Prompt #11

> I need to create an acceptance test for the m5 thin slice from end-to-end and unit tests for the logic underneath the thin slice. There should be units tests for the existing 86 tests from the thin slice. Please also provide suggestions on additional units tests is needed. I also need a coverage report generated for all of this. I will also need to inject a fault for a bug-injection demonstration, which need to show to the fault diff, the suit failing due to the bug injection, and the fix to restore the CI to green. The bug-injection should change the starting point of when shots are measured.

### AI Output

This section describes Claude Code's initial output. The changes made after my review are listed under Diff.

Claude Code proposed a plan before writing code, and I chose where the acceptance test runs: both on the JVM with Robolectric (so CI runs it) and on my phone.

Unlike earlier prompts, Claude Code could build and run the tests on my computer, using the Java 25 that comes with Android Studio.

- **Acceptance test:** `ThinSliceAcceptanceTest`, in a new `src/sharedTest` folder used by both the JVM tests and the device tests. It drives the real `TimerScreen` by its button labels through the M5 slice: Start, the start delay, the beep (a loud buffer during the beep must not count), two shots from 16-bit PCM buffers with room noise and an echo inside the cooldown, the times and splits on screen, Stop, the session saved as a JSON file, and a new ViewModel, engine and repository on the same folder still listing it. Only the hardware is replaced: microphone samples, beep, clock and delay scheduler. The real main-thread `Handler` and a real background thread are used.
- **Unit tests (10 new):** chosen from the gaps the coverage report showed. `ShotTest` (5: shot number and time rules), `FileSessionRepositoryTest` (2: storage folder cannot be created; other files in the folder are ignored), `TimerEngineTest` (1: two loud buffers at the same instant record one shot), `TimerViewModelTest` (1: closing the app while recording releases the microphone), plus the acceptance test.
- **Coverage:** AGP's built-in JaCoCo (`enableUnitTestCoverage`), and CI now generates and uploads a `coverage-report` artifact.
- **Fault survey:** Claude Code applied 12 candidate one-line faults one at a time (off-by-one, boundary, inverted condition and dropped-edge-case faults), ran the suite on each, and restored the file. All 12 were caught. I chose the timing-origin fault for the demonstration.

Problems Claude Code ran into and fixed:

1. AGP 9 does not compile Kotlin from a folder added with `java.srcDir`; the shared folder had to be added with `kotlin.directories`.
2. Robolectric 4.17 failed on JDK 25 until four `--add-opens`/`--add-exports` JVM flags were added to the test task.
3. The starter project's Espresso 3.5.1 calls `InputManager.getInstance()`, which no longer exists on new Android versions. Claude Code upgraded Espresso to 3.7.0 and `androidx.test.ext:junit` to 1.3.0 (with `androidx.test:rules` 1.7.0). This also affected the device run.
4. Its first fault-survey script did not record which tests failed and could have reused old results if a fault failed to compile. It found this, cleared the results before each run, and re-ran the survey.

### Code Changes

- New: `app/src/sharedTest/.../acceptance/ThinSliceAcceptanceTest.kt`, `app/src/test/.../domain/ShotTest.kt`, `evidence/m6/` (including `fault-survey.sh`).
- Moved: `EngineFakes.kt` from `src/test` to `src/sharedTest` (contents unchanged).
- Edited: `FileSessionRepositoryTest.kt`, `TimerEngineTest.kt`, `TimerViewModelTest.kt` (new tests only), `app/build.gradle.kts`, `gradle/libs.versions.toml`, `.github/workflows/Continuous_Integration.yml`.
- No production code under `app/src/main` was changed. The injected fault was reverted.
- Branch `m6-testing`. Commit: `c4fd70c`, in pull request #6. This log entry was added in a second commit on the same pull request, so that it could record the commit hash and the CI result.

### Diff

**Changes from Claude Code's initial output.** After reviewing the initial output, I asked Claude Code to make some improvements from a couple of my suggestions to the code. I also asked Claude Code to suggest additional improvements. It listed seven. I chose to apply all seven in addition to my two suggestions, and Claude Code made the edits at my direction:

1. **Removed two `ShotTest` tests** (`theFirstShotOfASession_hasNoSplit` and `aShotFiredTheInstantTheBeepStarts_isValid`). They only built a `Shot` and read its fields back, which restates the implementation instead of testing behaviour. Coverage was identical without them. `ShotTest` now has the three rejection tests.
2. **The acceptance test fires three shots instead of two.** With two shots there is only one split, so fastest, average and slowest were the same number. In the first fault survey, the "fastest split is the maximum" fault was not caught by the acceptance test. With splits of 0.40 s and 0.70 s, the screen must show fastest 0.40 s, average 0.55 s and slowest 0.70 s.
3. **The acceptance test states its own settings** (0.5 threshold, 200 ms cooldown, 3 s fixed delay) instead of using `TimerSettings.DEFAULT`, whose values are placeholders that will be tuned.
4. **"Closing the app" in the acceptance test is now realistic.** The first ViewModel is created in a `ViewModelStore`, and the store is cleared as Android does when the app closes. The test checks that the microphone is released before the app is opened again.
5. **The fault-survey script was added to the repository** as `evidence/m6/fault-survey.sh`. The first survey used a script that existed only on Claude Code's side, so it could not be re-run.
6. **CI caches Robolectric's Android runtime** (`~/.m2/repository/org/robolectric`) with `actions/cache@v6`, so it is not downloaded on every run.
7. **The full coverage HTML was removed from `evidence/m6/`** (about 1.5 MB). The repository keeps `coverage-summary.txt`; the full report is the CI `coverage-report` artifact.
8. **The `!!` in the acceptance test's saved-file check was replaced** with an assertion that gives a clear message if the file is missing.
9. **The `ViewModelProvider` boilerplate in `TimerViewModelTest` was shortened** with a small `viewModelIn` helper.

Change 2 made the screen taller, so the "Past sessions" row was below the visible part of the list and the acceptance test's `waitUntilShown` helper could not find it. Claude Code changed the helper to scroll while it waits.

### Result

All results below are for the final code, after the nine changes listed under Diff. The tests, coverage, bug injection and fault survey were re-run after the changes.

JVM run on my computer: 94 of 94 tests passed (86 earlier plus 8 new: 7 unit tests and the acceptance test). Coverage: 69.1% of lines and 70.7% of branches overall; domain 100% of lines, engine 98.7%, data 93.7%, ui 81.5%. Coverage was the same before and after removing the two `ShotTest` tests. The 0% classes are the Android hardware adapters, the factory, `MainActivity` and the theme, which need a real device.

Device run on my phone (CPH2647, Android 16), with the final test: `ThinSliceAcceptanceTest` and the starter instrumented test passed, 2 of 2 (the acceptance test took 2.0 s). The first two attempts after the review changes failed before the test started: the phone refused to let the test grant the microphone permission over USB (`SecurityException: Neither user 2000 nor current process has android.permission.GRANT_RUNTIME_PERMISSIONS`). This is a OnePlus restriction on granting permissions over USB, not a test failure. I granted the microphone permission to the app myself in the phone's settings, and the test was run with the app left installed so the permission was kept.

**Fault survey.** All 12 candidate faults were caught. The acceptance test caught 6 of them (A, D, E, J, K and L in `fault-survey.txt`); before change 2 it caught 4.

**Bug injection.** The fault adds one line to `TimerEngine.onBeepFinished()`, `beepStartedAt = clock.elapsed()`, which moves the timing origin from the start of the beep to its end. The app still runs and detects shots, but every shot time is short by the length of the beep (300 ms on a device), which breaks US03 and NFR 01. With the fault, 2 of 94 tests failed:

- `TimerEngineTest.shotAfterTheBeep_isTimedFromTheStartOfTheBeep`: `expected:<1.35s> but was:<1.2s>`
- `ThinSliceAcceptanceTest`: the screen did not show `#1   1.25 s   split —`

After `git checkout` of the file, 94 of 94 passed. Evidence is in `evidence/m6/` (fault diff, failing run, revert, passing run, coverage, device run and the 12-fault survey).

CI run on the pull request: run #15 of `Continuous_Integration` on commit `c4fd70c` passed in about 3 minutes. Every step succeeded, including the new Robolectric cache step, `Run unit tests` (which includes the Robolectric acceptance test) and `Generate coverage report`. The run uploaded the `unit-test-report`, `coverage-report` and `app-debug-apk` artifacts. The downloaded `unit-test-report` shows 94 tests, 0 failures, 0 skipped, and 100% successful, in 11.908 s; `ThinSliceAcceptanceTest` took 10.373 s of that under Robolectric. The downloaded `coverage-report` matches the local coverage report exactly, overall and for every package: 356 of 515 lines (69.1%) and 157 of 222 branches (70.7%).


## Claude Code — Documented Refactor

### Prompt #12

> Please help me improve the internal structure of the app without changing what is does, and prove that is what happened. Please suggest a refactor in the codebase that is genuinely worse that it should be and name the smell. We will need to run the same tests before and after the refactor. Both tests must pass, unchanged. Please include what was wrong, what was done to improve the codebase, and what is now easier.

Followed by the M7 assignment text, pasted unchanged: improve the internal structure of the app without changing what it does, name the smell, run the same tests before and after (unchanged), and write a short note on what was wrong, what was done and what is now easier.

### AI Output

Claude Code read the app code and listed four candidate smells, checked against the rule that the tests must not change:

- A: scattered state-transition rules in `TimerEngine` (recommended)
- B: a long function mixing display rules with layout in `TimerScreen`
- C: a Boolean result with two meanings in `TimerEngine.start()`
- D: duplicated JSON key strings in `SessionJson`

It ruled out C, because fixing it would change `start()`, which the tests call. It warned that B is only partly protected by tests.

I chose A. Claude Code's initial output, before my review:

- **Smell named:** duplicated, scattered conditional logic, with the timer's state machine left implicit. "Idle or Completed" was written out three times (`start()`, `updateSettings()`, and the Start button in `TimerScreen`). `start()`, `stop()` and `onBeepFinished()` each had their own guard. A hidden `state = Idle` write in `start()` skipped the method that notifies the screen.
- **Change:** the transition rules moved into `TimerState` (`canMoveTo(next)`, a single table, and `isAtRest`). `setState()` was replaced by `moveTo(next)`, the only place the engine changes state, which checks the table, notifies the listener, and returns false for a move the table does not allow. Every transition, including the required ones in `start()` and `stop()`, called `moveTo()`. In `start()`, its result was not used. The duplicated guards and the hidden write were removed. No public method of `TimerEngine` changed.
- **Proof:**
  - Claude Code ran the full suite on unchanged `master` before the refactor and again after it: 94 of 94 passed both times.
  - It recorded SHA-256 checksums of all 14 test files and the list of tests run, and showed both were identical before and after.
- **Supplementary check:** Claude Code broke the new transition table in two ways and showed the unchanged suite caught both (3 tests and 1 test), recorded in `evidence/m7/table-fault-check.txt`.
- **Note:** `REFACTOR_NOTE.md` said the hidden `state = Idle` write was gone and that the table allows `Completed → Preparing` directly, "which is what the screen always saw", without explaining why removing the intermediate `Idle` does not change behaviour.

### Code Changes

- Edited: `engine/TimerState.kt`, `engine/TimerEngine.kt`, `ui/TimerScreen.kt` (3 files, 68 lines added, 26 removed, final version).
- New: `evidence/m7/` (diff, before and after test runs, test-set proof, `REFACTOR_NOTE.md`).
- No test files were changed.
- Branch `m7-refactor`. Commit: `3fe9679`, in pull request #7. This log entry was added in a second commit on the same pull request, so that it could record the commit hash and the CI result.

### Diff

**Changes from Claude Code's initial output.** After reviewing the initial output, I asked for three changes, and Claude Code made the edits at my direction:

1. **The note now explains the `Completed → Preparing` change explicitly.** The refactor changed starting a new session after a finished one from `Completed → Idle → Preparing` to `Completed → Preparing`, and the note's wording was confusing about this. I asked for it to state that this preserves the externally observable behaviour. The intermediate `Idle` was assigned without notifying the listener, inside one synchronized `start()` call, so the screen, the ViewModel and the tests only ever saw `Completed` followed by `Preparing`.
2. **Removed the supplementary table fault check.** The assignment's proof is the unchanged suite passing before and after. The mutation-style check was not part of that proof, and describing temporary code changes in a "nothing changed" milestone could be misread. `table-fault-check.txt` was deleted, and its mentions were removed from the evidence README and the note.
3. **Required transitions are now explicit.** In the initial output, `start()` had already checked that a transition was allowed and then called `moveTo()` without using its result. I asked for required transitions to be explicit, but not for event callbacks, where an outdated callback is expected to be ignored. Claude Code added `requireMoveTo(next)`, which fails with an error if the table ever rejects the move. It is used for the four required transitions in `start()` and `stop()`. `onBeepFinished()` still calls `moveTo()` directly, with a comment explaining that a late callback is ignored. `stop()` now returns `true` explicitly after the required move.

Because change 3 changed the code, the after test run, the before-and-after diff and the test-set proof were regenerated afterwards. The same 94 tests passed, and all 14 test files still had identical checksums. The before run was not changed, because it is the run on unchanged `master`.

Decisions I made during the prompt:

- **Smell to refactor:** from the four candidates, I chose the scattered state-transition rules (A).
- **Log:** I asked Claude Code to draft this entry for my review.

### Result

Before the refactor: 94 of 94 tests passed. After the refactor: the same 94 tests passed, with no test file changed.

CI run on the pull request: run #18 of `Continuous_Integration` on commit `3fe9679` passed in about 4 minutes. Every step succeeded, including `Run unit tests` (with the Robolectric acceptance test) and `Generate coverage report`. The run uploaded the `unit-test-report`, `coverage-report` and `app-debug-apk` artifacts. The downloaded `unit-test-report` shows 94 tests, 0 failures, 0 skipped, and 100% successful, in 15.922 s, with the same per-class counts as the before and after runs; `ThinSliceAcceptanceTest` took 14.058 s of that under Robolectric.
