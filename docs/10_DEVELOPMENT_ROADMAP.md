# 10 — Claude Code Development Roadmap

Use **incremental feature delivery**, not a single massive "make Zeno" prompt. Before each milestone Claude must confirm Android permissions/role constraints, tests and rollback.

## M0 — Repo foundations and realistic feasibility spikes
- Inspect folder and current contents, establish Git, Kotlin/Compose Gradle project and test scaffolding.
- Document installed Android Studio/SDK/JDK versions; choose build/target SDK appropriately at implementation time.
- Implement `Action`, `Trigger`, `PolicyDecision`, `VoiceState`, `Routine` domain interfaces with fake adapters.
- Produce two early device spikes: selected assistant role registration/hotword viability and boot-after-first-unlock lifecycle; do not promise always-on until tested.
- Tests: compile and domain tests, UI basic navigation.

## M1 — Local settings and offline model integration
- DataStore defaults, permission/status diagnostics, model manager/checksums, optional offline ASR or fallback.
- UI: Settings, model status, Home `listening paused/ready` states.
- Tests: offline only, permission revoke, local model missing.

## M2 — Wake word, speaker enrollment, voice dialogue
- Configured supported hotword, owner speaker embeddings, anti-replay baseline, interactive sessions, TTS, context/cancel.
- Never confuse voice verification with secure unlock.
- Tests: FAR/FRR, playback, noise, latency and memory.

## M3 — Non-root phone actions
- AccessibilityService when explicitly enabled; normal APIs first: HOME/BACK/SCREENSHOT/LOCK, flashlight, brightness/volume, installed apps, notifications if allowed.
- Tests: protected fields, rotated screen, accessibility unavailable.

## M4 — Contacts, SMS and calls
- Contact alias resolver; multi-turn compose; `Immediate / Always confirm / Smart` setting; default assistant/SMS permission fit; carrier callbacks and simulated sends.
- Tests: missing SIM, no network, duplicate name, ambiguous message, canceled send.

## M5 — General-purpose automation library (big milestone)
- Room data model, schema migration, CRUD, trigger/conditions/action builder, approval diff, exact/inexact timing, idempotent run ledger, logging and pause-all.
- Test 150 routines, reboot reconciliation, missed run, DST/timezones, duplicate send prevention.

## M6 — Always-on assistant and startup hardening
- Selected VoiceInteractionService integrated with sessions, minimal boot flags, work rescheduling after unlock, mic restrictions and true status UI.
- Test app swiped away, screen off, first unlock, thermal/battery restrictions and permission toggles. Document unavoidable failure modes.

## M7 — Optional root allowlist
- Separate RootBridge with typed operations, Magisk permission prompt, off-by-default toggles, root errors, high-risk confirmations.
- Test root absent, denied, revoked, granted; verify no command string can enter shell. **Do not install Magisk or modify bootloader from code.**

## M8 — Adaptive learning
- Opt-in event sources, consent, local aggregation, pattern proposals, approve/edit/dismiss, privacy retention/deletion; approved routines run in M5 engine.
- Test no observation when off, never suggest/exe sensitive behavior without review.

## M9 — Polish, security and deployment
- 31-screen parity, accessibility, log redaction, backup/restore, battery profiling, privacy review, release signing, APK sideload instructions.
- 8-hour overnight baseline, multiple reboot and offline tests, clear release notes and known limitations.

## Milestone gates and deliverables
Every milestone must supply: architecture impact note, implemented REQ IDs, updated `docs/IMPLEMENTATION_STATUS.md`, green tests, screenshots/recording for UI, a tested debug APK where relevant, actual Pixel acceptance notes, and unresolved blockers. Do not mark DONE because code was typed; only mark once tested.

## Suggested working cadence with Claude
`Read CLAUDE.md → inspect repo → propose one milestone plan → implement a small slice → run tests → review UI + permission behavior → record results → commit`. Avoid uploading private contacts, raw voice, SMS or root credentials to any code assistant; use fake fixtures.
