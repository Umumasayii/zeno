# SPIKE-01 — Default assistant role, hotword APIs and screen-off microphone

**Status:** **RUN on device 2026-11-21** (Pixel 4a 5G `bramble`, Android 14 `UP1A.231105.001.B2`, patch 2023-11-05, not rooted, spike installed with `adb install`). Requirements: Z-002, Z-003, Z-029; ADR-002, ADR-007.

## Questions
1. Can a sideloaded app be chosen as the *Default digital assistant* on the Pixel 4a 5G (Android 14)? Does `RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)` work, or only the Settings screen?
2. Which hotword APIs are visible at runtime to a sideloaded assistant? (Desk finding: they are `@SystemApi` at compile time.) Are `MANAGE_HOTWORD_DETECTION` / `CAPTURE_AUDIO_HOTWORD` granted? (Expected: no.)
3. Does a microphone foreground service started from the visible app keep receiving real audio with the screen off and the phone locked, for 5 minutes?
4. Can a microphone foreground service be started when the assistant service starts (`onReady`), with no UI visible? Is the audio silenced?

## Procedure (owner runs; nothing is sent anywhere)
1. On the phone: enable Developer options and USB debugging. Connect USB. On the PC: `adb devices` should list the phone.
2. Build and install: `gradlew.bat :spike:assembleDebug`, then `adb install -r spike\build\outputs\apk\debug\spike-debug.apk`.
3. Open **Zeno Spike** → *1. Grant microphone + notifications*.
4. *2a. Request assistant role*: note what happens. Then *2b. Open Digital assistant settings*: select **Zeno Spike** if it is listed. Return to the app.
5. Tap *Log permissions*. The log should show `VIS onReady`, `PERMISSIONS@VIS …` and `HOTWORD_API …` lines.
6. *3. Mic probe 60 s*: within 10 s press power to turn the screen off; wait for the run to finish. Repeat with *3b* (5 min, screen off and locked).
7. Turn on *Probe mic when assistant service starts*. Then in Digital assistant settings switch to another assistant and back to Zeno Spike, or reboot.
8. Export the log: `adb shell run-as com.zeno.spike cat /data/user_de/0/com.zeno.spike/files/spike_log.txt > spike-results\spike01.txt` (the folder is git-ignored). Paste the relevant lines into the table below.
9. Afterwards: restore your normal assistant and `adb uninstall com.zeno.spike`.

## Results
| # | Measurement | Result | Evidence |
|---|---|---|---|
| 1 | Zeno Spike listed / selectable as default assistant | **PASS.** Selectable in Settings → Default digital assistant app. (At first the owner didn't find it because Google was the current default.) `VIS onReady` fired on selection. | log 12:45:06Z, 12:47:41Z |
| 1b | `createRequestRoleIntent(ROLE_ASSISTANT)` outcome | **No dialog**, resultCode=0 (cancelled). The app cannot request this role; the owner must pick it in Settings. | log ROLE_REQUEST lines |
| 2 | Hotword methods/classes visible at runtime | Visible via reflection (`createAlwaysOnHotwordDetector`, `createHotwordDetector`, `HotwordDetectionService`, …). Visibility ≠ usability, see 2b. Not invoked. | log HOTWORD_API lines |
| 2b | MANAGE_HOTWORD_DETECTION / CAPTURE_AUDIO_HOTWORD granted | **Not granted.** `pm grant … MANAGE_HOTWORD_DETECTION` from adb fails: "not a changeable permission type" (signature/privileged). | adb output |
| 3 | 60 s probe started from the app, then screen off + locked | **PASS: 0/60 silent seconds**, `clientSilenced=false` throughout, including after keyguard locked at t≈35 s | log 12:51:35–12:52:35Z |
| 3b | 5 min probe, locked | Not run (3 already answered the question; long-duration/Doze behaviour moves to M6 battery tests) | — |
| 4 | FGS start from `onReady` (no Zeno UI visible) | **PASS**, 3 times: start accepted, 0/30 silent seconds. Logcat: `Background started FGS: Allowed … uidState: BFGS`, i.e. the system's binding of the assistant service gives Zeno the importance needed to start a microphone FGS. | log VIS_READY runs; logcat |

## Conclusions
- A sideloaded Zeno **can be the default assistant** (set in Settings, not requested from code).
- The **system hotword pipeline is not available** to it (privileged permission). ADR-007's path is confirmed.
- While Zeno is the default assistant, it **can start a microphone foreground service from the background and keep real audio with the screen off and locked**. This is the basis for always-on wake-word listening (M2b/M6).
- Not yet measured: hours-long runs, Doze/idle, battery cost, behaviour when another app records or during calls (M6).
