# 09 — Testing and Acceptance Plan

## Strategy
Unit test policy, parser, recurrence, dedupe and learning; instrumentation test permissions and UI; **physical Pixel 4a 5G / Android 14** test real audio, screen-off, reboot/first unlock, SMS SIM and Magisk when installed. An emulator cannot prove continuous listening/battery or carrier send reliability.

## Test environment
Device firmware build and Magisk status recorded; airplane/Wi-Fi/cellular configurations; SIM operator, exact alarm permission, DND/accessibility/assistant roles and battery setting recorded before test. Use synthetic test contacts and non-sensitive test messages; don't send real family messages in automated test runs.

## Critical scenario matrix
| ID | Condition | Expected |
|---|---|---|
| T01 | Fresh launch, no permissions | Setup explains blocked features; no secret microphone capture |
| T02 | Owner enabled wake word, app closed, screen off | Wake word detection tested and measured; status truthfully reported |
| T03 | Listening toggled OFF | Microphone released; reboots do not silently re-enable |
| T04 | Restart with first unlock pending | Boot-safe state restored; credential-protected actions wait; no false 'listening' status |
| T05 | Android denies mic/default assistant role | Clear degraded UI, no retry loop or fake success |
| T06 | Someone else imitates speaker | Verification rejects/clarifies; risky actions not run |
| T07 | Recorded owner's voice | Anti-replay attempts rejected when possible; high-risk action requires Android auth |
| T08 | User says 'send Mum...' but 2 matching contacts | Clarify which; do not guess |
| T09 | Dictated SMS in always-confirm mode | Read-back + spoken approval required |
| T10 | Dictated SMS in immediate mode | Eligible approved recipient sent after owner verification, logs outcome |
| T11 | SMS failed because no SIM | Error, no duplicate retry storm |
| T12 | Daily scheduled SMS 7 AM approved | Exactly one local attempt for that day's occurrence, no second 'send' after reboot |
| T13 | Run schedule when phone off | Never fabricate a send; missed-run policy applied after recovery |
| T14 | Edit scheduled recipient | Existing approval invalid, reapproval required |
| T15 | 150 automations across types | Search, filter, toggle, sort work; performance measured |
| T16 | Learning disabled | No learning observations, suggestions or inferred action executed |
| T17 | Repeated app opening with learning enabled | Proposal only; never executes before user approval |
| T18 | Root disconnected/revoked | Normal commands still work; root requests fail safely |
| T19 | 'Unlock my phone' while PIN required | Device asks for PIN/fingerprint; no bypass |
| T20 | Clear learning data | Observations and derived suggestions purged locally |

The complete machine-readable checklist is in `tests/TEST_CASES.csv`. Every P0 and P1 requirement needs at least one positive and negative test. Record actual results in `tests/DEVICE_RESULTS_TEMPLATE.md`.

## Release gates
- M0–M2: build passes; permissions are transparent; offline ASR, voice verification and wake word measured; no raw recordings stored.
- M3–M4: core device actions and synthetic SMS/call flows pass; no unapproved external effect.
- M5: schedules work across reboot/timezone changes, duplicates prevented and permissions revoked safely.
- M6: screen-off + first-unlock restrictions documented from real device tests.
- M7: optional Magisk bridge allowlisted; disabling root works; never reaches arbitrary command execution.
- M8: learning source opt-in is enforceable; suggestions never auto-execute.
- M9: 8-hour idle battery and RAM benchmark documented; final user acceptance.

## QA artifact format
Every defect report: ID, build commit, actual firmware, steps to reproduce, expected/actual, logcat redacted, severity, fix commit, regression test. No P0 unresolved at release.
