# SPIKE-01 — Default assistant role, hotword APIs and screen-off microphone

**Status:** Probe built (`:spike`), **NOT RUN on device yet**. Requirements: Z-002, Z-003, Z-029; ADR-002, ADR-007.

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
| 1 | Zeno Spike listed / selectable as default assistant | NOT RUN | |
| 1b | `createRequestRoleIntent(ROLE_ASSISTANT)` outcome | NOT RUN | |
| 2 | Hotword methods/classes visible at runtime | NOT RUN | |
| 2b | MANAGE_HOTWORD_DETECTION / CAPTURE_AUDIO_HOTWORD granted | NOT RUN | |
| 3 | 60 s probe, screen off: silent seconds / clientSilenced | NOT RUN | |
| 3b | 5 min probe, locked: silent seconds / clientSilenced | NOT RUN | |
| 4 | FGS start from `onReady` accepted? audio silenced? | NOT RUN | |

## Decision impact
- If 3 passes but 4 fails: the listening service must be started from a visible UI or other allowed trigger, and resumed by the user after it is killed. Show this honestly.
- If 1 fails: drop the `VoiceInteractionService` dependency; use the foreground service plus a launcher/quick-settings tile.
