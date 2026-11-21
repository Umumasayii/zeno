# 02 — System Architecture

## Architectural style
Native Android, Kotlin, Jetpack Compose. **Clean-ish modular architecture** with clear domain contracts, platform adapters and no dependency from business logic onto UI/framework details. Avoid dozens of Gradle modules early: begin with `app`, `core:domain`, `core:data`, `core:security`, `core:voice`, `feature:automation`, `feature:ui` if build speed permits; split further once working.

## Dependency and flow
`UI / Voice Session / Background Events` → `Intent Parser` → `Policy Engine` → `Task Planner` → `Action Router` → `Android API | Accessibility | Root Allowlist` → `Outcome Recorder` → `TTS/UI/Notification`.

`Manual rule / Scheduler / Context events / Learning proposal` → `Automation Validation` → `Policy Engine` → **same** task planner/action router.

`ModelManager` handles downloaded/sideloaded local model assets, version, checksums, license, activation and fallback. `VoiceIdentityStore` holds encrypted speaker embeddings; `RoutineStore` handles schedule/versioning/execution history; `SettingsStore` holds flags and grants.

## Major components and owned responsibilities
| Component | Responsibilities | Must not do |
|---|---|---|
| AssistantService | Android VoiceInteractionService integration and lifecycle | Run heavyweight ASR continuously, promise mic startup from boot receiver |
| HotwordEngine | Detect one configured supported wake word | Continuously transcribe all surrounding speech |
| SpeakerVerifier | Check owner voice and optional spoof signals | Pretend equivalent to Android biometric auth |
| OfflineASR | Transcribe command/dictation after wake word | Call a hosted speech API |
| DialogueManager | Clarify ambiguous recipient, long message, confirmation | Fabricate contact/command parameters |
| CommandParser | Produce typed intents and entities with confidence | Execute commands directly |
| PolicyEngine | Determine permissions, confirmation, lock state and risk | Trust a text string from ASR as root authorization |
| TaskPlanner | Break a validated command into bounded approved steps | Dynamically invent unbounded Android actions |
| ActionRouter | Choose normal API → accessibility → privileged adapter | Execute arbitrary shell text |
| RootBridge | Issue typed, allowlisted operations via `su` after Magisk grant | Arbitrary user-provided `su -c` |
| AutomationEngine | Parse/validate rules, schedule, run, log, pause, cancel | Auto-activate learned habits |
| LearningEngine | Aggregate minimal authorized observations and generate proposals | Observe without opt-in or record secret input fields |
| VoiceUI | Compose screens, overlay/session, TTS + accessibility | Hide active microphone/critical confirmation state |
| LocalStores | Room, DataStore, encrypted secrets/embeddings | Sync privately stored information to cloud |

## Android services and lifecycle
- Selected `VoiceInteractionService` is documented as a system-managed always-running service. Keep it light; coordinate with `VoiceInteractionSessionService` for interactive UI. Selection as default assistant is a user action and feature eligibility must be tested.
- **Do not** start a microphone foreground service from `BOOT_COMPLETED` on Android 14; platform restrictions apply. A Direct Boot receiver may restore minimal non-microphone state from device-protected storage and reconcile post-unlock.
- Credential encrypted data (contacts-derived aliases, voice embeddings, most routines) are unavailable before the first unlock. Save only a *minimal* `listening_enabled` and boot-safe flags in device-protected storage, assuming acceptable threat model.
- Screen-off listening is a device acceptance target, not an unconditional guarantee. Handle hardware mic toggle, permissions revocation, battery restrictions and Android process killing.
- WorkManager for flexible work/reconciliation; AlarmManager for user-intended precise timed actions with permission fallback; context triggers use appropriate system callbacks/events as Android permits.

## Voice session state machine
`DISABLED → STANDBY → HOTWORD_DETECTED → OWNER_CHECK → LISTENING → PARSING → CLARIFYING? → POLICY_CHECK → CONFIRMING? → EXECUTING → REPORTING → STANDBY`.
Alternative outcomes: `REJECTED_UNKNOWN_VOICE`, `UNSUPPORTED`, `ROLE_MISSING`, `PERMISSION_BLOCKED`, `CANCELLED`, `TIMEOUT`, `ERROR`, `LOCKED_UNAVAILABLE`. Timeout and cancel must release mic and any held locks.

## Typed intents and action separation
Every spoken command yields an intent such as `SendSms(recipientRef, messageText)`, `OpenApp(packageRef)`, `TakeScreenshot`, `ScheduleRoutine(ruleDefinition)`. Parser confidence and entity resolution are separate from policy and action execution. **No text-to-shell translation**. No assumed coordinates for UI navigation.

## Automation orchestration
Each routine has stable ID, owner-visible name, enabled state, version, approval record, trigger specification, optional condition tree, ordered typed action steps, scheduling precision, timezone, missed-run policy, per-step retry/backoff, and execution log. A run uses a durable `idempotencyKey = routineId + version + occurrenceKey`; reboots and event replay cannot send the same message twice within the recorded occurrence. `SENDING` with unknown carrier outcome needs reconciliation, not blind retry.

## On-device learning
Source only consented features and minimal event summaries (app category, time bucket, charging state, an explicit action class). Do not store message bodies, passwords, OTPs or content of other apps as learning features. Candidate → proposed routine → user review → explicit approval → activation. Deleting history must delete feature summaries and suggestions.

## Storage design
- Room: `Routine`, `RoutineVersion`, `Trigger`, `Condition`, `ActionStep`, `RoutineApproval`, `Run`, `StepRun`, `LearningEvent`, `PatternCandidate`, `Proposal`, `CommandHistory` (redacted by default).
- DataStore: user preferences (listening state, SMS policy, flags, language, theme, status indicators).
- Android Keystore + encrypted file/data: speaker embeddings, backup master keys. Note device/root compromise can undermine protections; do not promise impossible secrecy on rooted device.
- Minimal device-protected DataStore: boot and preference continuity (no contact messages or embeddings).
- Data retention: defaults 30 days for routine logs and learning observation summaries; user-adjustable; explicit purge available.

## Interoperability and possible app restrictions
Google Messages SMS read/write may require default SMS or assistant-role conditions; outgoing SMS permission subject to Android and, if Play-distributed, Google Play rules. WhatsApp/YouTube UIs are external dependencies and may change; label them best-effort with user-visible failure. SIM/carrier and internet needs are independent of Zeno's local inference.

## Architecture decision records
See `docs/decisions/ADR-001.md` through `ADR-005.md`. Any alternative architecture must explain power, security, Android 14 compatibility and test coverage.
