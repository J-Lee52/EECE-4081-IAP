# RB-001: Shots are not being counted

| | |
|---|---|
| **App** | Shot Timer (Android package `com.example.eece4081_iap`) |
| **For** | Anyone supporting a user of the app. You do not need to know the code. |
| **Time needed** | About 15 minutes, with the user's phone in hand |
| **Owner** | Repository owner: https://github.com/J-Lee52/EECE-4081-IAP |

## Symptom

A user reports one of these:

- "I fired, but the timer still says **Shots: 0**."
- "It counted my first shots, then **stopped counting**."
- "It **misses some shots**: I fired three times quickly and it shows one or two."

On the user's screen, the timer was showing **Recording — fire when ready** while they fired.

Follow this runbook anyway if the screen showed one of these instead. Step 3 covers them too:

- **Could not open the microphone…**
- **The microphone stopped working, so the run was ended…**
- **The start beep could not be played…**
- the screen never got past **Get ready — the beep is coming**

## What you need

- A computer with Android **platform-tools** (the `adb` program). Download: https://developer.android.com/tools/releases/platform-tools. Unzip it and open a terminal in that folder.
- A USB cable for the phone.
- **USB debugging** turned on for the phone: Settings → About phone → tap **Build number** 7 times, then Settings → System → Developer options → **USB debugging** on. Connect the cable and tap **Allow** on the phone's prompt.

Check the phone is connected. The phone should be listed with the word `device` after it:

```
adb devices
```

The app's logs contain only timings, loudness levels, app states and error types or codes. They contain no names, audio recordings or location. System error messages, which can include file paths, are logged only in verbose mode (see Escalate).

## Step 1: Capture the problem in the log

1. Clear old log lines:
   ```
   adb logcat -c
   ```
2. Start capturing the app's log into a file. Leave this terminal running:
   ```
   adb logcat -v raw -s ShotTimer:V > shottimer-log.txt
   ```
3. Ask the user to reproduce the problem in a safe setting. They tap **Start**, wait for the beep, fire (or clap sharply, close to the phone) **three times about a second apart**, then tap **Stop**.
4. In the terminal, press **Ctrl+C** to stop capturing.

Every line in `shottimer-log.txt` is one JSON record with the same fields:

```
{"v":1,"ts":<time>,"level":"INFO","component":"engine","event":"shot.recorded","runId":"3f9c1a2b","sessionId":"...","data":{...}}
```

- `event` says what happened.
- `runId` links every line from one tap of **Start**.
- `data` holds the details.

## Step 2: Find the attempt

1. Open `shottimer-log.txt` in a text editor and find the **last** line containing `"event":"run.started"`. Copy its `runId` value, for example `3f9c1a2b`.
2. Show only that attempt's lines:
   - Windows: `findstr "3f9c1a2b" shottimer-log.txt`
   - macOS or Linux: `grep 3f9c1a2b shottimer-log.txt`

If there is **no** `run.started` line at all, look for `"event":"permission.denied"` and go to row A in step 3.

## Step 3: Match what you see

Work down the table and stop at the **first** row that matches the attempt's lines.

| # | What the log shows | What it means | What to do |
|---|---|---|---|
| A | `permission.denied`, or `microphone.open_failed` with `"error":"SecurityException"` | The app is not allowed to use the microphone. | On the phone: Settings → Apps → the Shot Timer app → Permissions → **Microphone → Allow only while using the app**. Then repeat step 1. |
| B | `microphone.open_failed` with any other error | Another app is using the microphone (a phone or video call, voice recorder, voice assistant, screen recorder). | Ask the user to end calls and close recording apps, then repeat step 1. If it still fails, restart the phone. |
| C | `microphone.read_error`, followed by `run.stopped` or `run.abandoned` with reason `microphone_lost` / `MICROPHONE_LOST` (the user saw "The microphone stopped working…") | The microphone stopped delivering sound part-way through the run, so the app ended the run. Shots before the failure were kept and saved. | Ask the user to close and reopen the app, then repeat step 1. If it happens again, restart the phone. If it still happens, escalate. |
| D | `run.stopped` shows `"buffersHeard":0` | The app recorded but received no sound at all. | As row C. |
| E | Every `audio.level` line shows `"maxPeak":0.0` exactly | The microphone is muted. Android's microphone privacy switch makes the app receive perfect digital silence. A working microphone in a quiet room still shows small values such as `0.02` to `0.05`, so those are **not** this row. | Check the phone's **Microphone access** switch in Quick Settings (swipe down from the top) and turn it **on**. Check nothing covers the microphone (case, hand, pocket). Repeat step 1. |
| F | `audio.level` lines show a `"maxPeak"` well above zero but **below** `"threshold"` (for example `0.31` against `0.5`), and no `shot.recorded` | The shots are reaching the microphone, but not loudly enough to count. | Put the phone closer to the shooter, within about 1–2 m and not behind them or in a bag, and repeat step 1. If the shots are still below the threshold, lower it: on the timer screen tap **Settings**, move **Detection threshold** to just below the `maxPeak` the shots reached (for example `0.30` for shots at `0.31`–`0.34`), tap **Save**, and repeat step 1. Do not go much lower than needed, or room noise may count as shots. If shots still are not counted, **escalate** with the log. |
| G | `audio.level` lines with `"ignoredCooldown"` above 0 at the moments the user says shots were missed (in verbose mode, also `shot.ignored` lines with `"reason":"cooldown"`) | These sounds came within the shot cooldown of the previous shot, so the app treated them as an echo of it. A few per shot is normal: each shot echoes. | Shots closer together than the **Shot cooldown** count as one. The default, 80 ms, is shorter than human splits, so first check the cooldown in use: the `cooldownMs` value on the attempt's `run.started` line, or the summary line under the timer's status. If it was raised in **Settings**, lower it back toward 80 ms, tap **Save**, and repeat step 1. (The opposite problem, one shot counted twice, shows as two `shot.recorded` lines only tens of milliseconds apart; raise the cooldown a little to fix it.) |
| H | `beep.unavailable`, then `run.abandoned` with reason `BEEP_UNAVAILABLE` (the user saw "The start beep could not be played…") | The phone could not play the start beep. Without the start signal the times would be wrong, so the app stopped the run before recording. | Check the phone is not in silent or Do Not Disturb mode and the media volume is up, then repeat step 1. If it happens again, restart the phone. If it still happens, escalate. |
| I | `state.changed` lines never reach `"to":"Recording"`, with no `beep.unavailable` or `run.abandoned` line | The run never started recording: the start delay or the beep did not finish. | Ask the user to keep the app open on screen until the beep, then repeat step 1. If it happens again, escalate. |
| J | `shot.recorded` lines are present, but the user's screen showed fewer shots | The app counted the shots, but the screen did not show them. | Escalate. |

## Step 4: Confirm it is fixed

Repeat step 1. The problem is fixed when the attempt shows one `shot.recorded` line for each shot fired, and the user's screen shows the same count.

## Escalate

If no row matches, or a row says to escalate, first capture one more attempt with **verbose diagnostics** turned on. Error records then also include the system's error message, which the developer may need.

1. Turn verbose mode on:
   ```
   adb shell "setprop log.tag.ShotTimer VERBOSE"
   ```
2. Repeat step 1, saving the log as `shottimer-log-verbose.txt`.
3. Turn verbose mode off again. Type the quotes exactly as shown: double quotes around the whole command, two single quotes at the end.
   ```
   adb shell "setprop log.tag.ShotTimer ''"
   ```
   Check it is off: `adb shell getprop log.tag.ShotTimer` should print an empty line.

Verbose messages can include file paths or device details, so only share that file with the developer.

Then open an issue at https://github.com/J-Lee52/EECE-4081-IAP/issues and include:

- `shottimer-log.txt` (and `shottimer-log-verbose.txt`, if made) and the `runId` of the failing attempt
- the row you matched (or "no match")
- the phone model and Android version (Settings → About phone)
- the type of firearm or sound, and roughly how far the phone was from it

## Background (optional reading)

- **How detection works.** The app counts a shot when a sound's loudness (`peak`, from 0.0 to 1.0) reaches the detection threshold (`threshold`, 0.50 by default). Sounds within the shot cooldown after the previous shot (80 ms by default) are ignored as echoes. Both are set on the **Settings** screen, which the timer screen summarises in one line (for example "Delay 3.0 s · Threshold 0.50 · Cooldown 80 ms"), and each `run.started` log line records the values in use.
- **`audio.level` lines.** While recording, the app writes one `audio.level` line a second. It shows the loudest sound in that second (`maxPeak`) and how many loud sounds were ignored as echoes (`ignoredCooldown`). Comparing `maxPeak` with `threshold` is the quickest way to see why a shot was or was not counted. Individual echo lines (`shot.ignored`) are written only in verbose mode, to keep long sessions' logs short.
- **`run.stopped` lines.** The `run.stopped` line summarises the attempt: why it stopped (`reason`), `shots` counted, `buffersHeard` (sound blocks received, about 100 a second), the loudest sound heard (`maxPeak`), and the number of echoes ignored (`ignoredCooldown`).
