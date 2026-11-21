# 11 — Feasibility review and document conflicts (2026-11-21)

Audit of the planning package before M0. The other documents remain the source of truth; this file records risks, conflicts and the decisions taken (owner accepted the defaults on 2026-11-21).

## Technical risks
| ID | Risk | Status / mitigation |
|---|---|---|
| R1 | System hotword APIs (`AlwaysOnHotwordDetector`, `HotwordDetectionService`) are `@SystemApi`, so a sideloaded app cannot use them | **Confirmed on device** (SPIKE-01): `MANAGE_HOTWORD_DETECTION` cannot be granted even via adb. **Mitigation also confirmed:** as default assistant, Zeno can run a mic FGS from the background with real audio, screen off and locked. ADR-007. |
| R2 | Restart / first unlock: background microphone restrictions on Android 14 (the cited source is Android 15's page) | **Measured** (SPIKE-02): assistant `onReady` ≈5 s after first unlock can start the mic FGS. `BOOT_COMPLETED` arrived 71 s late; `USER_UNLOCKED` was not delivered. Resume trigger = assistant `onReady`. |
| R3 | Custom wake word needs a trained or compatible model; Porcupine's licence check may need the network | Benchmark openWakeWord vs sherpa-onnx KWS (ADR-007). |
| R4 | Sideload install path: SMS/Call-Log hard-restricted permissions (Android 10+) and "restricted settings" for Accessibility / Notification access (Android 13+) | Install with `adb install` (decision 5). Verify on device in M3/M4. |
| R5 | Device is end-of-life at Android 14 (no more security patches); rooting raises risk | Root stays optional and allowlisted (ADR-003). |
| R6 | Vosk-small accuracy for free-form dictation with the owner's accent | M2a benchmark: Vosk vs whisper.cpp tiny/base vs on-device `SpeechRecognizer`. |
| R7 | Expectation gap: "intelligent" vs a grammar/slot parser first | The command set grows with fixtures; an optional small local LLM comes later (docs/08). |

## Document conflicts and resolutions
| ID | Conflict | Resolution |
|---|---|---|
| C1 | `Archtecture/` v1 vs `docs/02` v2: module layout, policy/planner order, `android/` subfolder | Follow v2 and FILE_TREE (Gradle at root, policy before planner). ADR-006. v1 folder kept untouched until the owner approves archiving. |
| C2 | Roadmap order: actions before wake word (v1) vs wake word first (v2 M2) | **M2a** push-to-talk + ASR + parser + TTS + dialogue; **M2b** wake word + speaker verification. |
| C3 | SMS mode names (Instant/Immediate, Smart/Adaptive/Risk-adaptive) | `IMMEDIATE / ALWAYS_CONFIRM / SMART` in code (`SmsPolicy`). |
| C4 | "Shut down my phone" with no friction vs R3 confirmation | Decision 2: spoken confirmation for R3, no PIN. Root operation must also be enabled individually. R3 is never unattended in automations. |
| C5 | 38 PNGs vs "31 screens"; duplicate / misnumbered files (03, 04, 05, 08, 09, 11, 12, 13/14) | `UI Designs/SCREEN_INDEX.csv` names are canonical; other files are alternates. |
| C6 | Theme D-06: purple Screens vs blue Reference Mockups | Purple (decision 4). |
| C7 | D-05: reading messages aloud while locked | Default OFF (`allowPrivateReadoutWhileLocked = false`): unlock required. |
| C8 | Docs say Android 14 forbids a mic FGS from `BOOT_COMPLETED`; the cited page is Android 15 | Measure in SPIKE-02 instead of assuming. |

## Owner decisions (2026-11-21)
1. Wake word: foreground service + openWakeWord or sherpa-onnx (benchmark); Magisk system app later, optional.
2. R3 (shutdown/reboot/delete): spoken confirmation is enough.
3. Module layout per ADR-006.
4. Purple theme.
5. Install with `adb install`.
6. Gradle and libraries may be downloaded for building (build tooling only, not the app).
