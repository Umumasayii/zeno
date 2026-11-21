# 03 — Voice Command Library, Dialogue and Grammar

## Overview
Intent/slot-based understanding is the first shipping choice. A small local model identifies commands, while the parser handles contact names, times, app names, variable message text and phrases. Vosk does transcription; it is **not** a task planner or identity verifier. No static keyword table can guarantee understanding of all English requests. The supported intent set expands incrementally, backed by fixtures.

## Activation
The wake word model supports a configured, **trained/supported** phrase (`Hey Zeno` as placeholder), not necessarily arbitrary newly typed words. Voice enrollments are speaker-identity samples; they are NOT wake-word model training. Voice activation chain is wake word → verify speaker → capture command; allow push-to-talk fallback if wake-word fails or is disabled. When Zeno is speaking, use echo suppression and avoid responding to its own TTS.

## Supported intent groups
| Group | Canonical intent | Slots / safety notes |
|---|---|---|
| Messages | SEND_SMS | contactRef, free-form body; recipient resolution; confirmation as set |
| Messages | READ_SMS | scope/count/sender; require permission and lock policy |
| Messages | REPLY_SMS | conversationRef, body; confirm recipient/thread |
| Calls | START_CALL | contactRef, number optionally; prefer supported call API |
| Calls | END_CALL | check telephony role and permission; not always available |
| Contacts | FIND_CONTACT | alias/name; ambiguous match prompts clarification |
| Apps | OPEN_APP | package/displayName; restrict installed apps |
| Apps | SEARCH_IN_APP | app, query; best-effort accessibility |
| Navigation | HOME/BACK/RECENTS | Accessibility global action |
| Navigation | SCROLL/TAP/FOCUS | direction/count/visible element label |
| Input | DICTATE_TEXT | raw content into explicit focus, no passwords |
| Screen | TAKE_SCREENSHOT | Accessibility screenshot/global action or platform API |
| Screen | READ_SCREEN | visible accessible text; protected windows blocked |
| Phone | LOCK_SCREEN | Accessibility global action or device admin |
| Phone | POWER_MENU | Accessibility global action |
| Phone | REBOOT/SHUTDOWN | Magisk root allowlist; risk policy |
| System | SET_VOLUME | stream + relative/absolute target |
| System | SET_BRIGHTNESS | level and scope; user grant if needed |
| System | SET_FLASHLIGHT | on/off; CameraManager support |
| System | SET_DND | on/off; policy access |
| Notifications | READ_NOTIFICATIONS | notification access permission; privacy/locked policy |
| Media | PLAY/PAUSE/NEXT/PREVIOUS | MediaSession controls where supported |
| Clock | CREATE_ALARM/REMINDER | wall time/timezone/date ambiguity resolution |
| Routines | CREATE_ROUTINE | trigger/actions/user confirmation; avoid execution before approval |
| Routines | LIST/PAUSE/RESUME/DELETE_ROUTINE | disambiguate routine identifier; destructive confirm |
| Learning | ENABLE/DISABLE_LEARNING | explicit owner command; settings confirmation |
| Learning | REVIEW_PROPOSALS | preview learned candidates, never auto-authorize |
| Assistant | STOP_LISTENING/START_LISTENING | `STOP` always immediate; enabling requires explicit UI/authorized action |
| Assistant | CANCEL/REPEAT/HELP | global interrupt available in any dialogue |

## Free-form SMS example
1. `Hey Zeno, text my mother.` → speaker verification and `SEND_SMS` with missing body.
2. `What should I say?` (if needed) → user dictates arbitrary body text.
3. Resolve mother via contact alias or ask which contact when ambiguous.
4. Display/read back according to SMS policy. If explicit confirmation mode, `Should I send it?`.
5. User says `send`, `change it to ...`, `cancel`; execute only after valid authorization.
6. Use carrier callback to report submitted/delivered status if available. Do not claim delivered merely because `SmsManager` returned.

## Scheduling via voice
`Hey Zeno, every weekday at 7, send a message to [contact] saying [message]` parses as `CREATE_ROUTINE` (not SEND_SMS now). Confirm timezone (`Africa/Kigali` default when device uses that zone), recurrence, chosen SIM, and exact text. Preview all future side effects; receive explicit approval, then activate. The morning family message remains a **sample**, never installed by default.

## Dialogue principles
- A single utterance may contain `then`, `after that`, `if` and `unless`. Split into bounded typed steps and ask for clarification if unsupported.
- Follow-up context keeps last resolved contact/routine *only in that active dialogue*, with short expiration and explicit cancel.
- Spoken `cancel`, `stop`, `never mind`, or listening toggle interrupts actions when feasible; report if an SMS has already been submitted.
- Differentiate command execution from suggestions: `I usually...` should not create an automation without explicit create intent.
- Prompt for missing required parameters; do not hallucinate phone numbers, file paths, installed apps or messages.
- Error prompts: `I found two contacts named Alex; which one?`, `I can't send SMS without your selected SIM`, `That app's button is not accessible`, `I couldn't verify it's your voice`.
- Don't repeat private SMS content through speaker when screen is locked and owner security policy requires fingerprint/PIN.

## Confidence/risk gate (proposed thresholds; tune with real data)
- Wake word detection threshold tested to meet target false-positive frequency.
- Speaker verification outcome `verified / uncertain / denied`; uncertain → repeat; never upgrade uncertainty based solely on high ASR confidence.
- Intent confidence ≥ 0.85 for auto-execution of low-risk action; 0.60–0.85 clarify; < 0.60 decline/help.
- Risky actions (SMS, calls, root, deleting routines) require policy-specific confirmation irrespective of ASR confidence.

## Test corpus
See `specs/voice_command_examples.csv` for multiple utterance variations per intent and canonical parameters. These are **synthetic**, not live messages or personal contacts. All samples require unit tests against their expected intent group, with noisy/ambiguous/negative examples in `tests/TEST_CASES.csv`.
