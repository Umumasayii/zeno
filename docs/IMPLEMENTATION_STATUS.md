# Implementation Status — update after EVERY Claude Code work session

**As of 2026-11-21:** M0 code and JVM tests are in place. **No feature has been verified on the Pixel yet**, and nothing below is "Complete" until it passes on the device.

| Milestone | Status | Evidence |
|---|---|---|
| M0 — Android project skeleton + spikes | **IN PROGRESS**: build + JVM tests pass; device spikes NOT RUN | Session log 2026-11-21 |
| M1 — Settings/local ASR | TODO | — |
| M2a — Push-to-talk, offline ASR, parser, TTS, dialogue | TODO | split from M2 (docs/11, C2) |
| M2b — Wake word + owner verification | TODO | depends on SPIKE-01, ADR-007 |
| M3 — Standard device actions | TODO | — |
| M4 — Contacts/SMS/calls | TODO | — |
| M5 — Automation engine | TODO | domain contracts exist (see below) |
| M6 — Always-on/startup tests | TODO | depends on SPIKE-01/02 |
| M7 — Optional root controls | TODO | allowlist enum + port only |
| M8 — Adaptive learning | TODO | — |
| M9 — Polishing/device acceptance | TODO | — |

## M0 checklist
- [x] Git repository, `.gitignore` for secrets, models, voice data and backups
- [x] Gradle project at repository root: `:app`, `:core:domain`, `:core:testing`, `:spike` (ADR-006)
- [x] Domain contracts: `AssistantIntent`/`IntentKind`, `RiskLevel`, `PolicyEngine` + `DefaultPolicyEngine`, `AuthorizationGate`/`AuthorizedIntent`, `ActionRouter`, `RootOperation`/`RootBridge`, `VoiceStateMachine`, `Routine`/`Trigger`/`Step` (schema mirror), `IdempotencyKey`/`RunLedger`, `WeeklySchedule` (DST), `MissedRunPolicy`, approval fingerprints, `ConditionEvaluator`, `ZenoSettings` safe defaults
- [x] Fixture tests against `specs/voice_command_examples.csv` (348 rows) and `specs/examples/*.json` (10 routines)
- [x] Compose shell: Home / Commands / Automations / Settings with honest "not built yet" states
- [x] Manifest guard: no INTERNET, no permissions, no cloud backup
- [x] Spike probe APK built (SPIKE-01, SPIKE-02)
- [ ] **SPIKE-01 run on Pixel** (docs/spikes/SPIKE-01_assistant_role_hotword.md)
- [ ] **SPIKE-02 run on Pixel** (docs/spikes/SPIKE-02_boot_first_unlock.md)
- [ ] App installed and navigated on the Pixel (screenshots at default and large font)

## Session log — 2026-11-21 (M0)
- **Build:** Gradle 9.9.0, AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00, JDK = Android Studio JBR 25; minSdk/targetSdk 34, compileSdk 37.
- **Goals and requirement IDs (contracts and tests only, not complete):** Z-004 (kill switch in state machine), Z-005/Z-006 (speaker gate in policy), Z-009 (no fabricated entities: unresolved refs only), Z-011 (SMS policy, default Always confirm), Z-015 (root allowlist enum, off by default), Z-016 (unlock always needs device credential), Z-020 (idempotency key + ledger contract), Z-021 (approval fingerprint), Z-023 (learning off by default), Z-028 (no INTERNET, no cloud backup).
- **Commands/tests run:** `gradlew clean test :app:assembleDebug :app:assembleRelease :spike:assembleDebug :app:lintDebug` → BUILD SUCCESSFUL.
  - `:core:domain` unit tests: **55 passed, 0 failed** (policy 23, voice state machine 11, automation contracts 11, router/authorization/root 7, spec fixtures 3).
  - `:app` Robolectric tests: **8 passed, 0 failed** (navigation 5, manifest policy 3).
  - Lint: 0 errors. Remaining warnings: newer dependency versions available, plus `targetSdk` 34 (deliberate, ADR-006).
- **Device tests completed:** none. No phone was connected (`adb devices` empty).
- **Security/permissions review:** `:app` requests no permissions. `:spike` requests RECORD_AUDIO, FOREGROUND_SERVICE(_MICROPHONE), POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, plus two privileged permissions declared only to record that they are NOT granted. The spike never stores audio and has no INTERNET permission.
- **Findings:** hotword APIs are `@SystemApi` in the public SDK, so a sideloaded app cannot compile against them (ADR-007, docs/11 R1).
- **Blockers and known limits:** device spikes need the phone with USB debugging. Wake-word engine choice is pending a benchmark.
- **Next smallest task:** run SPIKE-01 and SPIKE-02 on the Pixel and record the results. Then M1: DataStore settings + editable Settings screen, permission/status diagnostics, model manager.
