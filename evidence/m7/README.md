# M7 Evidence — Documented Refactor

| Evidence | File |
|---|---|
| Before-and-after diff (app code only) | `refactor-before-after.diff` |
| Test output before the refactor (94 tests, 0 failed) | `test-run-before.txt` |
| Test output after the refactor (the same 94 tests, 0 failed) | `test-run-after.txt` |
| Proof that the test set is unchanged (git, SHA-256 of all 14 test files, list of tests run) | `test-set-unchanged.txt` |
| The written note: what was wrong, what was done, what is now easier | `REFACTOR_NOTE.md` |
| Prompt-and-diff log | `../../AI_PROMPT_LOG.md`, Prompt #12 |

**Refactor in one line:** the timer's state-transition rules, previously duplicated across `TimerEngine` and `TimerScreen`, now live in one table (`TimerState.canMoveTo`), and every state change goes through one method (`TimerEngine.moveTo`).

Both test runs used the same command (`./gradlew testDebugUnitTest --rerun-tasks`) on the same machine. The before run is on unchanged `master` (`5b60c3a`); the after run is on the refactored code. No file under `app/src/test`, `app/src/sharedTest` or `app/src/androidTest` was changed.
