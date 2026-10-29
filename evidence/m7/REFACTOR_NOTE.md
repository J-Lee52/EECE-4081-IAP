# M7 Refactor Note — Centralising the Timer's State Rules

## What was wrong

**Smell: duplicated, scattered conditional logic. The timer's state machine was implicit.**

NFR 03 says the timer is always in exactly one of five states and only moves between them in valid ways. The rules for which state may follow which were not written down in any one place in the code. They were spread across `TimerEngine` and the screen as separate `if` checks:

- "A session is not in progress" (`Idle` or `Completed`) was written out three times: in `TimerEngine.start()`, in `TimerEngine.updateSettings()`, and in `TimerScreen` to decide when to show the Start button.
- Each of `start()`, `stop()` and `onBeepFinished()` had its own guard, and four methods called a general-purpose `setState()` that would accept any state at all.
- `start()` also contained a hidden write, `state = TimerState.Idle`, that skipped `setState()` and so never told the screen. It worked, but it showed how easy it was to change the state without going through the normal path.
- The full set of allowed moves existed only in a comment at the top of `TimerEngine`.

To answer "can the timer go from X to Y?", or to add a new transition, you had to read every method in the engine and the screen, and keep all the copies in step by hand.

## What I did

1. **Moved the rules into `TimerState`.**
   - `canMoveTo(next)` is a single table of every allowed transition, with the diagram beside it.
   - `isAtRest` names the "no session in progress" rule (`Idle` or `Completed`).
2. **Replaced `setState()` with two helpers that make each transition's intent explicit.**
   - `moveTo(next)` is now the only place the engine changes `state`. It checks the table, applies the move, notifies the listener, and returns false for a move the table does not allow. It is used directly only by the event callback `onBeepFinished()`, where a late callback after the state has moved on is expected and should be ignored.
   - `requireMoveTo(next)` is used for every required transition in `start()` and `stop()`, where the caller has already checked that the move is allowed. If the table ever rejected one of these moves, that would be a bug, so it fails with an error instead of being silently ignored. On every path the tests exercise the move is allowed, so this adds no observable change.
3. **Removed the duplicated guards.**
   - `start()`, `stop()` and `onBeepFinished()` now ask the table.
   - `updateSettings()` and the Start button use `isAtRest`.
   - The hidden `state = Idle` write is gone, and starting a new session after a finished one is now modelled as `Completed → Preparing` directly, instead of `Completed → Idle → Preparing`. This preserves the externally observable behaviour. The intermediate `Idle` was assigned directly to the field, without calling `setState()`, so the listener was never notified of it. It happened inside the same synchronized `start()` call, so no other thread could read it either. The screen, the ViewModel and every test only ever saw `Completed` followed by `Preparing`, which is exactly what the refactored code produces.
4. **Kept everything else the same.**
   - No public method of `TimerEngine` changed its name, parameters or results.
   - Two checks were kept as they are: `onDelayElapsed()` and `onSamples()` ask "is this event relevant in the current state?", not "may the state change?", so they are not transition rules.

## How we know behaviour did not change

- **Same tests, unchanged.** The same 94 tests (93 unit tests and the M6 acceptance test) passed before and after (`test-run-before.txt`, `test-run-after.txt`).
- **Test files identical.** No test file changed: git shows no changes in the test folders, and all 14 test files have identical SHA-256 checksums before and after (`test-set-unchanged.txt`).

## What is now easier, and why

- **Adding "cancel during the start delay"** (the known limitation since M5) is now one new entry in the table, `WaitingForStart → Idle`, plus a small `cancel()` method written the same way as `stop()`: check `canMoveTo(Idle)`, release the microphone, then `requireMoveTo(Idle)`. The delay callback already ignores a call that arrives after the state has moved on. Before, a new transition meant finding and editing guards in several methods.
- **The settings screen (US01)** can show or hide its controls with `state.isAtRest`, the same rule the engine uses to accept or reject a settings change. The screen and the engine cannot disagree, because there is only one copy of the rule. The Start button already works this way.
- **Checking NFR 03** means reading one six-line function instead of six methods.
- **The state cannot change without the screen being told**, because `moveTo()` is the only place `state` is assigned, and it always notifies the listener.
