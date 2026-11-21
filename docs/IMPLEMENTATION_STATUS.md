# Implementation Status — update after EVERY Claude Code work session

**As of 2026-11-21:** M0 is done. M1 code and JVM/Robolectric tests are done; **M1 device acceptance is pending** (phone not connected). No voice feature exists yet; nothing below is "Complete" until it passes on the device.

| Milestone | Status | Evidence |
|---|---|---|
| M0 — Android project skeleton + spikes | **DONE** (build + 63 JVM tests pass; SPIKE-01/02 run on Pixel; app installs with 0 permissions). Open: screenshots at default/large font | Session logs 2026-11-21 |
| M1 — Settings, status diagnostics, model manager | **CODE DONE, device test pending** (103 JVM/Robolectric tests pass; 0 permissions). Offline ASR moved to M2a (docs/11 C2) | Session log 2026-11-21 (M1) |
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
- [x] **SPIKE-01 run on Pixel** (docs/spikes/SPIKE-01_assistant_role_hotword.md)
- [x] **SPIKE-02 run on Pixel** (docs/spikes/SPIKE-02_boot_first_unlock.md)
- [x] App installed on the Pixel via `adb install`; `dumpsys package` shows **no requested permissions**
- [ ] Screenshots at default and large font (phone was locked; deferred to M1 UI review)

## M1 checklist
- [x] Domain: `SettingsRepository` port, `ListeningStatusResolver` (Paused / Waiting for unlock / Permission blocked / Role missing / Engine unavailable / Not running / Active), `WakePhrasePolicy` (Z-001), model manifest + SHA-256/size/licence verification (`ModelInventory`)
- [x] DataStore settings (credential-encrypted) with safe per-field fallbacks and corruption → defaults; `listeningEnabled` + `startListeningAfterBoot` mirrored to device-protected storage for the M6 boot path
- [x] Android platform status: user unlocked, RECORD_AUDIO, assistant role (RoleManager), 14 capability grant states read live; root never probed
- [x] Owner-sideloaded model directory (`docs/MODEL_INSTALL.md`); path traversal refused; no network
- [x] Editable Settings (name, wake phrase, listening, start after restart, SMS policy, confirm calls, private readout while locked, retention, pause automations, restore defaults with confirmation). Learning (M8) and root (M7) shown but locked
- [x] Permissions & status screen with Digital assistant settings shortcut and app-info shortcut; Home shows the real status and a working listening switch
- [x] Manifest still requests no permissions and no INTERNET (ManifestPolicyTest)
- [ ] **Device:** install on Pixel, settings survive app kill + reboot, status screen matches `dumpsys`, synthetic model missing/corrupt/verified via `adb push`, screenshots at default and large font
- [ ] Offline ASR: deferred to M2a by design (needs RECORD_AUDIO and a licensed model)

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
- **Next smallest task:** run SPIKE-01 and SPIKE-02 on the Pixel (done, see the next session log).

## Session log — 2026-11-21 (M0 device spikes)
- **Device:** Pixel 4a 5G `bramble`, Android 14 `UP1A.231105.001.B2`, security patch 2023-11-05, not rooted. Spike installed with `adb install`.
- **SPIKE-01:** Zeno can be the default assistant (Settings only; the app cannot request the role). `MANAGE_HOTWORD_DETECTION` refused even via adb. Mic FGS keeps real audio with screen off + locked (0/60 silent seconds). Mic FGS can start from the assistant service in the background (`uidState: BFGS`).
- **SPIKE-02:** `LOCKED_BOOT_COMPLETED` delivered before unlock. Assistant `onReady` ≈5 s after the owner's first unlock, and a mic FGS works from there. `BOOT_COMPLETED` delivered 71 s after sending. `USER_UNLOCKED` not delivered to manifest receivers. Pre-unlock mic not measured (probe service not Direct Boot aware).
- **Cleanup:** Google restored as default assistant; spike auto-probe toggles set OFF and verified. Spike app left installed for re-runs; remove with `adb uninstall com.zeno.spike`. Raw log saved locally in `spike-results/` (git-ignored).
- **Security/permissions review:** no audio stored; only loudness numbers logged. The owner unlocked with their own PIN; Zeno never touched Keyguard.
- **Next smallest task:** M1: DataStore-backed settings with an editable Settings screen, permission/role status diagnostics (incl. "default assistant" status and the Settings shortcut), honest listening status model (`Active / Paused / Waiting for unlock / Permission blocked / Role missing`), model manager skeleton.

## Session log — 2026-11-21 (M1)
- **Goals and requirement IDs:** Z-001 (wake phrase only "supported" when a verified model lists it), Z-003/Z-004 (listening switch, honest status, off by default and after reset), Z-011 (SMS policy editable, default Always confirm), Z-026 (settings persist locally), Z-029 (status never claims listening without the service reporting it), Z-031 (restore safe defaults), Z-033 (permission/role checklist), Z-028 (no INTERNET, no backup). Z-023/Z-015 stay locked in the UI.
- **Dependencies added:** `androidx.datastore:datastore-preferences:1.3.0`, `androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0`. App version 0.1.0-m1.
- **Commands/tests run:** `gradlew clean test :app:assembleDebug :app:assembleRelease :spike:assembleDebug :app:lintDebug` → BUILD SUCCESSFUL.
  - `:core:domain`: **72 passed, 0 failed** (+17: listening status/wake phrase 7, model inventory 10).
  - `:app` Robolectric: **31 passed, 0 failed** (DataStore persistence/restart/mirror/corruption 6, platform + storage incl. mic revoked and role held 8, UI 14 at 393×851 dp, manifest policy 3).
  - Lint: 0 errors; warnings are newer versions available and targetSdk 34 (deliberate).
- **Device tests completed:** none yet (`adb devices` empty this session).
- **Security/permissions review:** still zero requested permissions. M1 only *reads* grant states. Boot-safe mirror holds two booleans, nothing personal. Models are owner-sideloaded, hash-verified, plain file names only; Zeno never downloads them.
- **Known limits:** wake-word engine and listening service don't exist yet, so with listening on, the status is always a blocker (the M1 build shows "Microphone permission needed"). Theme setting deferred to M9.
- **Next smallest task:** M1 device acceptance on the Pixel, then M2a (push-to-talk, offline ASR benchmark, parser, TTS).
