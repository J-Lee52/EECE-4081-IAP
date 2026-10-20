# M6 Evidence — Tests, Coverage and Bug Injection

| Evidence | File |
|---|---|
| Test run output (JVM: 94 tests, 0 failed) | `test-run.txt` |
| Acceptance test on a phone (CPH2647, Android 16: 2 tests, 0 failed; microphone permission granted by hand beforehand, see Prompt #11) | `device-run/TEST-CPH2647-Android16.xml` |
| Coverage report (JaCoCo) | `coverage-summary.txt`; full HTML report in the CI artifact `coverage-report` |
| Bug injection 1: the fault diff | `bug-injection-1-fault.diff` |
| Bug injection 2: the suite failing on it | `bug-injection-2-failing-run.txt` |
| Bug injection 3: the revert, and the suite passing again | `bug-injection-3-revert.txt`, `bug-injection-3-fixed-run.txt` |
| Survey of 12 candidate faults | `fault-survey.txt`, produced by `fault-survey.sh` |
| Prompt-and-diff log | `../../AI_PROMPT_LOG.md`, Prompt #11 |

## Acceptance test

`app/src/sharedTest/java/com/example/eece4081_iap/acceptance/ThinSliceAcceptanceTest.kt`

It drives the real `TimerScreen` through the M5 thin slice: Start, the start delay, the beep, three shots detected from 16-bit PCM microphone buffers, live times and splits on screen, Stop, the session saved as a JSON file, the app closed (microphone released) and reopened with the session still listed. The code between the screen and the disk is the production code. Only the hardware is replaced (microphone samples, beep, clock, delay scheduler). The same file runs on the JVM under Robolectric (in CI) and on a device.

## Injected fault

One added line in `TimerEngine.onBeepFinished()` moves the timing origin from the start of the beep to its end. The app still runs and still detects shots, but every shot time is short by the length of the beep (300 ms on a real device). This breaks US03 (the beep is the timing origin) and NFR 01 (0.01 s accuracy). Two tests caught it: the engine unit test (`expected:<1.35s> but was:<1.2s>`) and the acceptance test (the screen did not show `#1   1.25 s`).

## Coverage gaps that are expected

The 0% classes are the Android hardware adapters (`AndroidAudioInput`, `AndroidBeepPlayer`, `AndroidClock`, `MainThreadScheduler`), `TimerViewModelFactory`, `MainActivity` and the theme. They need a real microphone, speaker or device and are covered by the device run, not by JVM coverage.
