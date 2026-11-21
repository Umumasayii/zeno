# SPIKE-02 — Boot, Direct Boot and first-unlock lifecycle

**Status:** Probe built (`:spike`), **NOT RUN on device yet**. Requirements: Z-003, Z-032, NFR-08; ADR-002.

## Questions
1. In what order and at what times do `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED` and `USER_UNLOCKED` reach a Direct Boot aware manifest receiver on this phone?
2. Can a microphone foreground service be started from each of those broadcasts on Android 14? If it starts, is the audio silenced (all zeros / `isClientSilenced=true`)?
3. Is device-protected storage writable before first unlock (the log itself proves it)?

Note: the reference cited in docs (`behavior-changes-15#fgs-boot-completed`) describes **Android 15**. Android 14's own rules (background "while-in-use" mic restrictions) are what apply on this device; that is exactly what this spike measures.

## Procedure
1. Install the probe as in SPIKE-01 and grant the microphone.
2. In Zeno Spike turn on *Probe mic at boot / unlock*.
3. Reboot. **Wait about 2 minutes at the lock screen without unlocking**, then unlock.
4. Open Zeno Spike → *Refresh*, or export with `adb shell run-as com.zeno.spike cat /data/user_de/0/com.zeno.spike/files/spike_log.txt`.
5. Repeat once with the toggle OFF to confirm no probe runs (privacy check, T04).
6. Turn the toggle off and uninstall the probe when done.

## Results
| # | Measurement | Result | Evidence |
|---|---|---|---|
| 1 | Broadcast order and timestamps | NOT RUN | |
| 2a | Mic FGS from LOCKED_BOOT_COMPLETED | NOT RUN | |
| 2b | Mic FGS from BOOT_COMPLETED | NOT RUN | |
| 2c | Mic FGS from USER_UNLOCKED (if delivered) | NOT RUN | |
| 3 | Log written before unlock (`unlocked=false` lines) | NOT RUN | |
| 5 | Toggle OFF → no probe after reboot | NOT RUN | |

## Expected design consequence
Zeno restores a minimal `listening_enabled` flag from device-protected storage. Its status shows **"Waiting for unlock"** or **"Paused — tap to resume"** whenever Android refuses the microphone, and never claims to be listening (T04, NFR-08).
