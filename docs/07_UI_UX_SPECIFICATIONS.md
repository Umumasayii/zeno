# 07 — UI/UX Specifications (31 Screens)

## Design system
Jetpack Compose + Material 3, dark-first with light theme support, high contrast, 48dp minimum targets, Dynamic Type/sp accessibility, subtle blue/purple highlights, gentle waveform only while microphone is really active. Existing `UI Designs/Screens` PNG files are **references**; read their labels critically and prioritize behavior and accessibility over exact screenshot reproduction.

## Main navigation
**Home | Commands | Automations | Settings**. `Routines` may be the public label for Automations, but the feature is a general-purpose library. A persistent listening status indicator differentiates `Active / Paused / Waiting for unlock / Permission blocked / Role missing`.

## Onboarding flow
Welcome → assistant name/wake-word compatibility → offline model availability → voice enrollment and anti-replay explanation → permission checklist → default assistant selection → optional Root (never default) → SMS confirmation preferences → listening test → finish. Skipping permission yields a clear degraded capability, not broken onboarding.

## Screen-to-file map
| # | PNG | Intended screen and must-have functionality |
|---|---|---|
| 01 | `01_Welcome.png` | Intro with privacy/limitations; start/skip feature setup |
| 02 | `02_Introduction.png` | Feature tour; explain always-on, offline ≠ carrier offline |
| 03 | `03_Home.png` | True listening status, quick commands, recent outcomes, pause |
| 04 | `04_Commands.png` | Searchable command categories, phrase examples, permission badges |
| 05 | `05_Routines.png` | General searchable automation library; active/paused/failed chips |
| 06 | `06_Voice_Training.png` | Owner enrollment, sample quality test, retrain, delete |
| 07 | `07_Settings.png` | Settings categories including learning, startup, root, voice and SMS |
| 08 | `08_SMS_Behavior.png` | Instant/Always confirm/Adaptive, sample dialog, send readback |
| 09 | `09_Voice_Lock_Screen.png` | Lock-screen restrictions; never claim voice alone securely unlocks |
| 10 | `10_Root_Permissions.png` | Magisk connection, per-command allow/deny, audit logs |
| 11 | `11_App_Permissions.png` | Granted/revoked/error for each permission and role |
| 12 | `12_Listening_Overlay.png` | Wake detected, verify owner, transcription and cancel |
| 13 | `13_SMS_Confirmation.png` | Accurate contact/body/SIM/cost review, send/cancel/edit |
| 14 | `14_SMS_Sent.png` | Distinguish submitted vs carrier delivered; error and retry caution |
| 15 | `15_Create_Routine.png` | Generic ordered steps, trigger and condition builder |
| 16 | `16_Teach_Command.png` | Guided action recording, redaction, validation and preview |
| 17 | `17_Assistant_Name.png` | Set display name and compatible wake word; model training status |
| 18 | `18_Offline_Battery.png` | Language-model status, performance/privacy/battery statistics |
| 19 | `19_Background_Startup.png` | Listening toggle, start after boot, screen-off support & real status |
| 20 | `20_Adaptive_Learning_Settings.png` | Opt-in OFF by default, suggestions/execution approved, retention |
| 21 | `21_Learned_Routines.png` | Proposal list, approvals, dismissal, no-learn source control |
| 22 | `22_Learning_Suggestion.png` | Evidence, proposed trigger/actions, approve/edit/dismiss |
| 23 | `23_Create_Scheduled_Automation.png` | Schedule recurrence + **arbitrary** action; SMS only sample |
| 24 | `24_Schedule_Repeat.png` | Day/date/time/repeat/timezone/DST/missed run precision UI |
| 25 | `25_Automation_Details.png` | Edit/duplicate/pause/delete, version, next run and latest outcome |
| 26 | `26_Automation_History.png` | Success/failure/unknown, redacted details and filters |
| 27 | `27_Learning_Privacy.png` | Sources toggles, retention/export/delete learned data |
| 28 | `28_Boot_Resume_Status.png` | Post reboot waiting-for-first-unlock and blocked statuses |
| 29 | `29_Automation_Approval.png` | Explicit per-version side-effect confirmation, enable/deny |
| 30 | `30_Automation_Center.png` | All custom routines, search, categories, triggers, bulk pause |
| 31 | `31_Listening_Paused.png` | Honest listening-off state and easy restore |

## Additional UI subflows (not separately mocked)
- Permission denied/role not selected, unsupported language, no SIM/no network, ambiguous contact selection.
- Add automation wizard with multiple actions, `if/else`, conditions, validation, dry run, and run-now confirmation.
- Routine edit diff and reapproval, cancel active run, stop all, notification channel settings.
- Security: voice rejection, anti-spoof challenge, PIN/fingerprint required and privacy shutter.
- Settings: export/import encrypted configuration; wipe voice data; reset app.

## States per main screen
For each screen Claude must implement: loading, populated, empty, denied-permission, error, and offline-only variant where relevant. Every toggle must be backed by real DataStore value and service effect. Buttons must work and show outcomes; decorative controls must not be displayed as interactive.

## Interaction details
- Home listening toggle: immediate stop to capture. If disabled, show `Listening paused` not a listening animation; dedicated UI action re-enables it.
- Automation library: names not limited to SMS; filter and searchable by action type, state, scheduled/learned/manual, failure; editable with explicit approval expiration.
- Voice enrollment: actual microphone recording, clear progress/failure, local speaker embedding generation. Do not hardcode 20 samples as a guarantee.
- Voice overlay: never appear as 'listening' when Android denies mic; show helpful remediation.
- Schedule creation: previews next 3 occurrences and timezone, displays delivery precision if exact alarm permission denied.
- Learning: first enable asks for accessible observation categories; proposal count separate from running routine count.

## UI validation checklist
1. Screenshot on a Pixel 4a 5G at large and default font sizes.
2. TalkBack content descriptions, headings, focus order, contrast.
3. Dynamic theme (dark/light), rotation safe states, 60Hz performance.
4. All buttons and toggles wired to real state or explicitly disabled with explanation.
5. Clear error feedback, idempotent repeated taps, secure actions available by voice and by touch.
