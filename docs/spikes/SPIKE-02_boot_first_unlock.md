# SPIKE-02 — Boot, Direct Boot and first-unlock lifecycle

**Status:** **RUN on device 2026-11-21** (Pixel 4a 5G `bramble`, Android 14 `UP1A.231105.001.B2`, not rooted). Requirements: Z-003, Z-032, NFR-08; ADR-002.
Setup: Zeno Spike was the default assistant; "probe mic at boot" and "probe on assistant start" toggles were ON; `adb reboot` at 12:54:40Z. The owner waited at the lock screen, then unlocked with their own PIN at about 12:58:08Z. Zeno never unlocks the device.

Note: the reference cited in docs (`behavior-changes-15#fgs-boot-completed`) describes **Android 15**. On this Android 14 device the behaviour was measured below.

## Timeline (UTC)
| Time | Event |
|---|---|
| 12:54:40 | `adb reboot` |
| 12:55:04 | Boot animation ends (≈20 s after kernel start) |
| 12:55:04.9 | System sends `LOCKED_BOOT_COMPLETED` |
| 12:55:06.4 | Spike receiver gets it, **before unlock** (`unlocked=false`), and writes to device-protected storage |
| 12:55:06.5 | Mic FGS start from that broadcast: `Unable to start service … MicProbeService: not found`, because the probe service is not `directBootAware`, so it does not exist before unlock |
| ≈12:58:08 | Owner unlocks with PIN |
| 12:58:13.4 | **Assistant service `onReady` ≈5 s after unlock**, with no app opened |
| 12:58:13.5 | Mic FGS started from `onReady`: `Background started FGS: Allowed … uidState: BFGS`; **0/30 silent seconds** |
| 12:58:13.9 | System sends `BOOT_COMPLETED` |
| 12:59:24.3 | Spike receiver gets `BOOT_COMPLETED`, **71 s after it was sent** (broadcast queue delay) |
| 12:59:24.4 | Mic FGS from `BOOT_COMPLETED`: allowed, 0/30 silent seconds. **Confounded:** uidState was TOP (the spike UI was in front) and a BOOT_COMPLETED temp-allowlist applied, so this is not a clean background measurement |
| — | `USER_UNLOCKED` was **never delivered** to the manifest receiver |

## Results
| # | Measurement | Result |
|---|---|---|
| 1 | Broadcast order and timestamps | `LOCKED_BOOT_COMPLETED` (pre-unlock, ~2 s after boot anim) → unlock → `BOOT_COMPLETED` (sent ~5 s after unlock, delivered 71 s later). `USER_UNLOCKED` not delivered to manifest receivers. |
| 2a | Mic FGS from `LOCKED_BOOT_COMPLETED` | **Not measured:** the probe service wasn't Direct Boot aware ("not found"). Optional re-run with a `directBootAware` service. Low priority: voice profiles live in credential storage and are unavailable before unlock anyway. |
| 2b | Mic FGS from `BOOT_COMPLETED` | Allowed, real audio, but confounded (app was TOP). Not relied upon. |
| 2c | Mic FGS from `USER_UNLOCKED` | Broadcast not delivered; not usable. |
| 2d | **Mic FGS from assistant `onReady` after reboot** | **PASS:** started 5 s after unlock, real audio, no UI needed. |
| 3 | Device-protected storage writable before unlock | **PASS** (`unlocked=false` log line). |
| 5 | Toggle OFF → no probe after reboot | Not run as a separate reboot. The toggles were reset to OFF after the test (prefs file verified). |

## Design consequences (input to M1/M6)
1. **Resume trigger = assistant service `onReady`,** not boot broadcasts. When Zeno is the default assistant, Android restarts it ≈5 s after first unlock, and from there it may start the microphone foreground service. `BOOT_COMPLETED` is too late (71 s here) and was not cleanly tested.
2. Before first unlock Zeno shows/keeps **"Waiting for unlock"**. Only a minimal `listening_enabled` flag lives in device-protected storage (docs/02).
3. Do not depend on `USER_UNLOCKED` manifest broadcasts.
4. If Zeno is **not** the default assistant, auto-resume after reboot is not established; the UI must say "Tap to resume listening" (T04, NFR-08).
