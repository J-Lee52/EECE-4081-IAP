# M8 Evidence: Structured Logging and Runbook

**Repository:** https://github.com/J-Lee52/EECE-4081-IAP

| Evidence | File |
|---|---|
| Sample structured log lines (real capture from a phone) | `device-log-capture.txt`, `device-log-capture-quiet.txt` |
| One operation traced across log lines (a normal run) | `traced-run-5b1ef293.txt` |
| The runbook's symptom, traced (a run where no shots counted) | `traced-run-f18d1fb9-quiet.txt` |
| Runbook entry, committed | `../../docs/runbook/RB-001-shots-not-counted.md` |
| Prompt-and-diff log | `../../AI_PROMPT_LOG.md`, Prompt #13 |

## How the samples were captured

On 8 Nov 2026 the final build was installed on a OnePlus CPH2647 (Android 16). The log was captured using the runbook's own step 1 (`adb logcat -c`, then `adb logcat -v raw -s ShotTimer:V > shottimer-log.txt`), with verbose mode off, as in normal use.

| File | Runs it contains |
|---|---|
| `device-log-capture.txt` | A normal run of sharp claps (`5b1ef293`), then a run meant to be quiet in which one loud sound was counted (`c5cd0818`) |
| `device-log-capture-quiet.txt` | A second quiet run, made without that loud sound (`f18d1fb9`) |

Both files are unedited. Each first line is Logcat's own header.

## Field structure

Every record is one JSON object on one line, with the same eight top-level fields in the same order:

```
{"v":1,"ts":1794187296125,"level":"INFO","component":"engine","event":"shot.recorded","runId":"5b1ef293","sessionId":"…","data":{"shot":2,"atMs":1554,"splitMs":481,"peak":0.934}}
```

| Field | Meaning |
|---|---|
| `v` | Format version (1) |
| `ts` | Time of the record, epoch milliseconds |
| `level` | `DEBUG`, `INFO`, `WARN` or `ERROR` |
| `component` | Part of the app that wrote it: `engine`, `viewmodel` or `storage`. Microphone and beep failures are reported by the audio adapters and logged by the `engine` on the affected run's trace. |
| `event` | Stable dotted name of what happened, such as `run.started` or `shot.recorded` |
| `runId` | **Correlating id**: created each time Start is tapped, carried on every record of that run; `null` outside a run |
| `sessionId` | Id of the saved session, from the start beep onwards; `null` before then |
| `data` | Event-specific details. Durations are whole ms with an `Ms` suffix; loudness is 0.0–1.0. Errors are described by stable type names or codes. System error messages appear only in verbose diagnostic mode, as `data.diagnostics`. |

All 52 records in the two captures parse as JSON and have exactly these fields. The format is fixed by `LogJsonTest`.

## The traced operation (`traced-run-5b1ef293.txt`)

Filtering on `runId` 5b1ef293 (runbook step 2: `findstr "5b1ef293" device-log-capture.txt` returns exactly these 19 lines) gives one tap of Start, followed through two components to the saved file:

1. `run.started` with the settings in use, then `state.changed` Idle → Preparing.
2. `microphone.opened`, then `state.changed` Preparing → WaitingForStart.
3. `beep.started`: the session is created, and `sessionId` appears from here on.
4. `state.changed` WaitingForStart → Recording.
5. Five `shot.recorded` lines with each shot's time and split. Between them, one `audio.level` line per second gives the loudest sound and `ignoredCooldown`, the number of clap echoes ignored in that second (0, 1, 2, 0). Echoes are counted rather than logged one by one; individual `shot.ignored` lines appear only in verbose mode.
6. `run.stopped` with a summary (`"reason":"user"`, 5 shots, 388 sound buffers, loudest 0.941, 3 echoes ignored), then `state.changed` Recording → Completed.
7. `session.saved` from the **viewmodel**, with the same `runId` and `sessionId`, then `history.loaded`.

`RunTraceLoggingTest` checks this same sequence automatically.

## The runbook's symptom, traced (`traced-run-f18d1fb9-quiet.txt`)

In the quiet run, each `audio.level` line shows a `maxPeak` between 0.033 and 0.212 against a `threshold` of 0.5. `run.stopped` reports 0 shots and 0 echoes, and `session.not_saved` follows with reason `no_shots`. This is runbook row F: the sounds reached the microphone, but not loudly enough to count.

The first capture's second run (`c5cd0818`) was also meant to be quiet, but one sound reached 0.916 and was counted as a shot, so the quiet run was repeated.

Earlier captures, made before the review changes, also corrected the runbook. A working microphone in a quiet room measured `maxPeak` 0.019–0.049, so row E (muted microphone) was changed to "exactly 0.0". Testing the verbose-mode commands showed that `adb shell setprop log.tag.ShotTimer ""` does not work (adb drops the empty argument), so the runbook uses the quoted form, which was tested in PowerShell, Command Prompt and Git Bash.
