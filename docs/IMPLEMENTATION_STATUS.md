# Implementation Status — update after EVERY Claude Code work session

**As of 2026-11-21:** M0 is done. M1 code and JVM/Robolectric tests are done; **M1 device acceptance is pending** (phone not connected). No voice feature exists yet; nothing below is "Complete" until it passes on the device.

| Milestone | Status | Evidence |
|---|---|---|
| M0 — Android project skeleton + spikes | **DONE** (build + 63 JVM tests pass; SPIKE-01/02 run on Pixel; app installs with 0 permissions). Open: screenshots at default/large font | Session logs 2026-11-21 |
| M1 — Settings, status diagnostics, model manager | **CODE DONE, device test partly done** (103 JVM/Robolectric tests pass; on Pixel: install, status, persistence after force-stop pass; model/reboot/large-font pending). Offline ASR moved to M2a (docs/11 C2) | Session logs 2026-11-21 (M1, M1 device) |
| M2a — Push-to-talk, offline ASR, parser, TTS, dialogue | **CODE DONE, device test pending** (139 JVM/Robolectric tests pass; parser held-out accuracy 88 %; on-device recognizer + TTS not yet run on the Pixel) | Session log 2026-11-21 (M2a), ADR-008 |
| M2b — Wake word + owner verification | **PART 1 CODE DONE** (engine-independent: audio quality, enrollment, verifier, replay guard, calibration, wake pipeline, encrypted profile, Voice Training screen; 179 JVM/Robolectric tests pass). **Part 2 blocked on the engine decision (ADR-009)**; SPIKE-03 ready, not run | Session log 2026-11-21 (M2b part 1), ADR-009, SPIKE-03 |
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
- [x] **Device:** install on Pixel (0 permissions), status screen matches `dumpsys`/`cmd role`, settings survive force-stop, boot flags written to device-protected storage, locked switches stay off, default-font screenshots
- [ ] **Device (pending, phone unavailable):** settings after reboot, synthetic model missing/verified/corrupt via `adb push`, large-font screenshots, TalkBack pass on the fixed Home switch
- [ ] Offline ASR: deferred to M2a by design (needs RECORD_AUDIO and a licensed model)

## M2a checklist
- [x] Rule-based command parser for the docs/03 command groups (`RuleBasedCommandParser`): ordered rules, slots kept as spoken references (never resolved or guessed), multi-step sentences refused as unsupported, assistant-name prefix from settings
- [x] Parser fixtures: every docs/03 fixture maps to its exact intent; held-out phrasings written after the rules: **35/40 (88 %)**, all 5 misses are "not understood", never a different command
- [x] Dialogue manager: missing-slot questions (SMS body, reply, dictation, reminder), read-back, explicit confirmation, "change it to …", cancel/stop from any state, one re-ask then cancel, no command switching while confirming, policy re-checked at confirmation, repeat/help
- [x] `CommandSource.PUSH_TO_TALK` policy: locked phone → Android credential; no voice identity claims (ADR-008)
- [x] Android on-device speech recognizer only (`createOnDeviceSpeechRecognizer`), offline pack check, network errors reported as not allowed; one utterance per tap
- [x] TTS with an offline voice only; replies spoken only for spoken input; TTS stopped before the mic opens
- [x] Typed command field (same parser/policy), touch Confirm/Cancel buttons, in-memory transcript (30 lines), 30 s dialogue timeout
- [x] Only action in M2a: "stop listening" (real adapter behind the router). Every other understood command is explained with its milestone, never confirmed, nothing sent
- [x] Manifest: `RECORD_AUDIO` only (asked on first mic tap), no INTERNET (ManifestPolicyTest, `aapt2 dump permissions` on the release APK)
- [ ] **Device (pending):** offline pack detection, push-to-talk in airplane mode, partial results, TTS voice offline, mic permission deny → revoke in Settings → status, green mic indicator only while listening, locked-phone refusal, TalkBack on the panel
- [ ] Speech recognition accuracy on the Pixel (word error rate on the docs/03 fixtures, read aloud by the owner)

## M2b checklist
- [x] Audio analysis in memory: dBFS levels, noise floor, speech time, clipping, SNR; sample quality gate with owner-actionable reasons
- [x] Command endpointer (energy VAD): trailing silence, no-speech timeout, max length, short bursts ignored, wake-phrase pre-roll kept
- [x] `SpeakerEmbedder` port; `VoiceProfile` (embeddings only) bound to the exact speaker model and version (other model → STALE, never used)
- [x] Enrollment: varied prompts, quality gate before the model runs, consistency check against earlier samples, minimum 5 samples (configurable, not a guarantee), maximum 20
- [x] Verifier: verified / uncertain / denied with two thresholds (placeholders until calibrated on device); no profile or a stale profile never verifies
- [x] Baseline replay guard: near-identical embeddings of recent commands or enrollment samples → uncertain. Does NOT detect new recordings or synthetic voices (stated in UI and ADR-009)
- [x] Calibration maths: FAR, FRR, EER, threshold for a target FAR (scores only, no audio)
- [x] `WakeWordDetector` port, `WakeScoreFilter` (patience, refractory, ignores Zeno's own TTS), `WakePipeline` (wake → capture → owner check → hand-off; unverified speech is dropped without transcription; kill switch drops buffers)
- [x] Listening status `VOICE_NOT_ENROLLED`; the engine counts as ready only with both wake-word and speaker models verified
- [x] Encrypted voice profile store: AES-256-GCM, Android Keystore key, `noBackupFilesDir`, fresh IV per save, tampered/unreadable file discarded, delete removes file and key (T014)
- [x] Voice Training screen (06): honest "no speaker model installed" state (nothing recorded), training progress, prompts, record/stop, level meter, quality feedback, finish/cancel, test my voice, delete with confirmation, limits text
- [x] SPIKE-03 code in `:spike` (on-device recognizer with piped audio) + procedure
- [x] No new app permissions in part 1 (release APK: RECORD_AUDIO + receiver guard only)
- [ ] **Owner decision:** engine and model download (ADR-009)
- [ ] **Part 2:** engine adapters behind `WakeWordDetector`/`SpeakerEmbedder`, listening foreground service (adds FOREGROUND_SERVICE_MICROPHONE + POST_NOTIFICATIONS), notification with stop action, voice commands through `CommandSource.VOICE`
- [ ] **Device:** SPIKE-03, SPIKE-04 benchmark (false wakes/hour, CPU, battery, FAR/FRR/EER with consenting non-owners and playback), Keystore store on the Pixel, enrollment UX

## Session log — 2026-11-21 (M2b part 1)
- **Context:** the owner asked to keep building while the phone is unavailable. Engine choice is a licensing and download decision for the owner (ADR-009), so only engine-independent work was done. Nothing is marked Complete.
- **Goals and requirement IDs:** Z-001 (wake phrases come from the installed model), Z-004 (pipeline kill switch drops audio), Z-005 (enroll, test, retrain, delete; unknown/low-confidence speaker denied), Z-006 (replay baseline; limits stated), Z-029 (`VOICE_NOT_ENROLLED` status), Z-031 (profile deletion erases the key), Z-028 (profile never backed up; audio never stored).
- **Decisions:** ADR-009 proposed (recommend sherpa-onnx after a SPIKE-04 benchmark; owner to approve). Verification runs on the wake phrase plus command, and nothing is transcribed before the owner is verified (SPIKE-03 decides how ASR gets that audio).
- **Commands/tests run:** `gradlew clean test :app:assembleDebug :app:assembleRelease :spike:assembleDebug :app:lintDebug :spike:lintDebug`.
  - `:core:domain`: **121 passed, 0 failed** (+27: audio 7, voice identity 11, wake pipeline 8, status 1).
  - `:app`: **58 passed, 0 failed** (+13: encrypted store 4 with a software key, voice training ViewModel 8, navigation 1).
- **Device tests completed:** none (phone unavailable).
- **Security/permissions review:** no new permissions. Voice profile = embeddings only, AES-GCM with a non-exportable Keystore key, no backup. Recording for enrollment happens only from the Voice Training buttons and only when a speaker model is installed (none in this build). Test fixtures are synthetic tones; no real voice in the repo.
- **Known limits:** thresholds are placeholders until calibrated on the device. The replay guard only catches near-identical replays. The Keystore key isn't exercised in JVM tests (software key instead).
- **Next smallest task:** owner decides ADR-009. Then the joint device session: M1 leftovers, the M2a checklist, SPIKE-03, then SPIKE-04 with the chosen engine.

## Session log — 2026-11-21 (M2a)
- **Context:** the phone became unavailable; the owner asked to build everything now and run the device tests together later. Nothing in M2a is marked Complete.
- **Goals and requirement IDs:** Z-004 (spoken "stop listening" turns listening off), Z-007/Z-008 (command parsing, multi-turn dialogue), Z-009 (references stay unresolved; nothing fabricated), Z-011 (SMS read-back wording; no SMS can be sent in M2a), Z-016 (locked phone needs Android unlock), Z-028 (no INTERNET; on-device recognizer only), Z-029 (mic indicator only while a session is open).
- **Decisions:** ADR-008 (Android on-device recognizer + offline TTS voice instead of bundling a model in M2a; push-to-talk identity = Android unlock). New optional `notBuilt` hook in `DialogueManager`, consulted after policy and before confirmation.
- **Commands/tests run:** `gradlew clean test :app:assembleDebug :app:assembleRelease :spike:assembleDebug :app:lintDebug` → BUILD SUCCESSFUL.
  - `:core:domain`: **94 passed, 0 failed** (+22: parser fixtures/slots/negatives 8, held-out phrasing 2, dialogue 12).
  - `:app` Robolectric/JVM: **45 passed, 0 failed** (+14: assistant ViewModel 11 with scripted speech, Home panel UI 3).
  - Lint: 0 errors; warnings are newer versions available and targetSdk 34 (deliberate).
  - Release APK `0.2.0-m2a` (versionCode 3): permissions = `RECORD_AUDIO` + AndroidX receiver guard.
- **Device tests completed:** none (phone unavailable).
- **Security/permissions review:** one new runtime permission (RECORD_AUDIO), requested only from the mic button. No foreground service, no background capture, no audio or transcript written to disk. No SMS, call, contact or root code exists yet.
- **Known limits:** speech quality depends on Google's offline English pack; without it the mic is disabled with instructions. Held-out misses ("shoot a text to …", "do I have any new messages", "give Mum a call", "fire up the camera", "forget about it") are left for later rule work. Confirm buttons exist but no M2a command needs confirmation.
- **Next smallest task:** joint device session: M1 leftovers (reboot, synthetic models, large font, TalkBack; then delete the test `manifest.json`) and the M2a device checklist above. Owner decides whether the default assistant stays `com.zeno.spike` or goes back to Google. Then M2b (wake word + owner verification), starting with the wake-word engine benchmark (ADR-007).

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

## Session log — 2026-11-21 (M1 device, partial)
- **Device:** Pixel 4a 5G, Android 14. Installed `app-debug.apk` 0.1.0-m1 with `adb install -r`. `dumpsys package`: no runtime permissions; only the AndroidX receiver-guard permission.
- **Passed:** Home "Listening paused" → switch on → "Microphone permission needed" (never a listening state). `dumpsys audio` shows no recording by Zeno; `appops get RECORD_AUDIO`: no operations. Status screen: unlocked = Yes, assistant = "Not Zeno" (`cmd role get-role-holders` = Google at the time), all 14 capabilities "Not granted", root "Off · not checked", models "No models installed" with path `/storage/emulated/0/Android/data/com.zeno.assistant.debug/files/models`. "Open Digital assistant settings" opens `Settings$ManageAssistActivity`. Name "Ada", Smart confirmation, 7 days, automations paused and listening on all survived `am force-stop`. `zeno_boot_flags.xml` in `/data/user_de/0/...` held `listening_enabled=true`, `start_listening_after_boot=true`. Tapping the disabled learning and root switches changed nothing.
- **Found and fixed:** the Home listening switch label and switch were separate accessibility nodes (uiautomator dump). The row is now one `toggleable` switch. Re-tested in Robolectric (14/14); not yet re-checked on the phone.
- **Observed during the test:** the default assistant role changed to `com.zeno.spike` through the on-device picker at 15:43 (not by an adb command). The spike's mic probe was not running and no new recordings were logged. Owner to restore Google or keep it.
- **Not done:** reboot persistence, synthetic model checks (a test `manifest.json` was pushed to the models folder and is still there), large-font screenshots, TalkBack. Phone became unavailable.
