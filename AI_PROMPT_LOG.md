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

I adopted the CI-first order and the branch-and-pull-request workflow. Storage choice (Room or file-based) is still open: `<fill in when decided>`.


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

Added `.github/workflows/Continuous_Integration.yml` in commit `<hash of first commit>`, on branch `walking-skeleton`.

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

I made no modifications to Claude's Kotlin files. I committed the domain files and tests exactly as drafted so that the AI's code could be tested as-is. At this time, the only change I have made to any of Claude's output is the name change to the CI workflow described under Prompt #6.

I accepted the four flagged design decisions as drafted for now. Any later revisions will be recorded in a separate entry.

### Result

Local run in Android Studio: 20 of 20 tests passed

CI run on the pull request: the build and test steps passed. The downloaded `unit-test-report` artifact shows 21 tests, 0 failures, 0 skipped, and 100% successful, in 0.548 s under Gradle 9.6.0 (4 + 9 + 7 new tests plus the starter `ExampleUnitTest`).

Pull request #1 & #2 was merged into `master` with a merge commit, so the commit hashes in this log stay valid.

ADR-001 says to check the implementation against its `AudioInput` and `ShotDetector` boundaries once those classes exist. They do not exist yet, so that check is still pending.