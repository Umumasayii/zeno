# 01 — Product Requirements Document (PRD)

**Status:** Requirements baseline; implementation pending. **Target:** Pixel 4a 5G (`bramble`), Android 14. **Primary user:** one device owner. **Distribution:** personal sideloaded APK initially; possible future expansion requires reassessment.

## Product promise
Zeno helps the owner operate their phone almost completely by voice while keeping control logic and personal data offline. It also executes **user-defined, pre-authorized routines** and can optionally propose new routines from locally observed repeated behavior.

## Goals and non-goals
**Goals:** wake word when enabled; app-closed/screen-off operation where Android permits; user voice enrollment; talk, navigate, read and compose SMS, calls, apps, notifications, screen; normal and privileged phone controls; robust creation and management of numerous scheduled/contextual automations; adaptive learning opt-in; minimal battery impact; fully local data.

**Non-goals for v1:** a general LLM that understands any possible request; guaranteed secure voice-only unlock; guaranteed always-on microphone immediately after boot before first unlock; guaranteed automatic control inside all third-party apps; automated financial transfers; bypassing enterprise/work profile or bank authentication; cloud sync; unreviewed automatic execution inferred from data.

## Priority meanings
- **P0:** Required to deliver trustworthy foundations.
- **P1:** Core product behavior.
- **P2:** Advanced feature after P1 stability.
- **SPIKE:** Need physical-device/API verification before promise.

## Functional requirements
| ID | Priority | Requirement | Acceptance condition |
|---|---|---|---|
| Z-001 | P0 | User chooses Zeno's display name and supported wake word. | Save/restore name; unsupported custom hotword requests explain that new model training may be required. |
| Z-002 | P1 | Standby hotword listening whenever enabled, incl. screen off where possible. | Real-device screen-off tests pass within measured latency, no raw audio stored by default. |
| Z-003 | P1 | Restore listening preference after app closure/reboot. | State persists; after boot/first unlock, app correctly reports active/degraded/blocked, never pretends to listen. |
| Z-004 | P0 | One explicit listening kill switch. | Disabling immediately releases mic resources, persists and cancels listening retries. |
| Z-005 | P1 | Enroll owner voice and verify speaker before handling a spoken command. | Enrollment can be tested, retrained, deleted; unknown/low-confidence speaker denied. |
| Z-006 | P0 | Protect sensitive actions against replay/spoofing and uncertain identities. | Secondary Android authentication required for high-risk actions; playback tests recorded. |
| Z-007 | P1 | Offline English recognition for commands and free-form message dictation. | No network required for model inference; reasonable benchmarks measured on device. |
| Z-008 | P1 | Contextual multistep dialogue, corrections, cancel and retry. | `Text Mum` → `What message?` → dictate → review → send works. |
| Z-009 | P1 | Contacts lookup with ambiguous names resolution. | Never guess when multiple contacts match; no number fabricated. |
| Z-010 | P1 | SMS compose/send/read/reply where permissions and role allow. | Success/failure states tied to carrier callbacks, not optimistic UI. |
| Z-011 | P0 | User-configurable SMS behavior: instant, always confirm, adaptive. | Settings persist and apply to unscheduled voice-initiated messages. |
| Z-012 | P1 | Phone call initiation, speaker controls when supported. | Routes using approved API/role; locked state and failures handled. |
| Z-013 | P1 | Home/back/recents, scrolling, pressing UI elements, dictation. | Tested under accessibility with safe boundary and clear error feedback. |
| Z-014 | P1 | Lock, screenshot, flashlight, brightness, media and notifications. | Uses non-root APIs first; permission checks and failure statuses displayed. |
| Z-015 | P2 | Enable approved privileged actions through Magisk. | Root not mandatory for app startup; typed allowlist prevents arbitrary shell strings. |
| Z-016 | P0 | No automatic bypass of Android secure lock screen. | PIN/fingerprint required where Keyguard demands it; voice can wake or request dismissal. |
| Z-017 | P1 | Create/edit/duplicate/enable/pause/delete many manual automations. | Search/filter/sort and bulk pause; no fixed built-in 7AM/Mum logic. |
| Z-018 | P1 | Automation triggers: time, interval, boot/first-unlock, battery, charging, selected events. | Trigger availability and precision stated; missed-event recovery specified. |
| Z-019 | P1 | Automation conditions and ordered multi-step actions. | Boolean AND/OR rules; stop/continue rules; step-by-step history. |
| Z-020 | P0 | No duplicate automated SMS on reboot, retry or same event. | Durable idempotency and exact-once *attempt* per scheduled occurrence. |
| Z-021 | P1 | Per-routine permissions, recipients, costs and confirmation policy. | Creation previews side effects and stores explicit approval version. |
| Z-022 | P1 | Automation logs, pause, error notification, retry policies, failure recovery. | Every attempt has timestamp, status, reason and user control. |
| Z-023 | P2 | Adaptive learning opt-in, disabled by default. | No behavior observation for learning until owner enables it. |
| Z-024 | P2 | Local repeated-pattern suggestions with approval. | Repetition alone never causes execution; suggestion dismissal respected. |
| Z-025 | P2 | Approved learned routines can be scheduled automatically. | Enabling `Execute approved routines` still requires routine-level approval. |
| Z-026 | P0 | Settings for voice/language, confirmation, permissions, root, startup, learning. | Export, import, change and delete; settings survive normal restarts. |
| Z-027 | P1 | Local offline TTS with voice/volume/speed options. | Test at setup; missing language pack has fallback and instruction. |
| Z-028 | P0 | Sensitive data stays on device; app network is unnecessary for inference. | No analytics, cloud calls, remote endpoint or unencrypted voice-profile export. |
| Z-029 | P1 | Clear status: listening, unavailable, permission blocked, speaker verified, executing. | UI/notification reflects reality and includes cancel control. |
| Z-030 | P2 | Teach a command via demonstrated, reviewed steps. | Replay robust to UI changes or fails safely; never records passwords/OTP. |
| Z-031 | P1 | Full local deletion/reset and retention controls. | Delete profile/history/routines separately and together; verify data removed. |
| Z-032 | P1 | Restore scheduled work after reboot/timezone change. | Reconcile exactly once after first unlock if credentials required. |
| Z-033 | P1 | A first-run wizard asks for required roles and permissions progressively. | Features degrade gracefully when permissions/roles refused. |
| Z-034 | P2 | Optional second language pack(s) including Kinyarwanda only after license/quality validation. | UI does not advertise a verified model without testing. |

## Nonfunctional requirements and initial performance budgets
These are **targets to measure**, not proven numbers:
- NFR-01 Offline command-to-action p95 ≤ 2.5 s *after utterance end* on supported simple actions, excluding carrier/app latency.
- NFR-02 Wake-word trigger p95 ≤ 1.5 s and reasonable false triggers at measured noise levels; quantify false accepts per hour.
- NFR-03 Idle incremental battery drain target ≤ 3% of battery per 8 hours on test Pixel (screen off, fixed radio conditions); revise based on measured feasibility.
- NFR-04 Idle RSS target ≤ 150 MB for hotword service; large ASR models loaded only when active. Measure with Android profiling.
- NFR-05 100+ saved automations in test library, no unacceptable search/filter delay; no arbitrary product ceiling.
- NFR-06 Every destructive/risky action has auditable permission and confirmation outcome; 0 unapproved execution in acceptance tests.
- NFR-07 Accessibility text readable at large text/display scaling and usable without relying on colors alone.
- NFR-08 Boot persistence should report **degraded until unlock** where Direct Boot/permission limitations apply; never silently claim success.

## In-scope examples, not hardcoded behaviors
- "Hey Zeno, take a screenshot." 
- "Text Mum: Good morning. Please remember your medicine."
- "Every weekday at 7 AM, send this message to [selected contact]" — **example only**; supports all recipients/schedules/actions.
- "At 10 PM, enable DND, dim the screen, set an alarm and lock the phone."
- "Read the newest unread SMS and reply 'I'll be there soon'."

## Default behaviors proposed for safety
- Listening: **ON after setup** with user opt-out, not covertly enabled before consent.
- Learning: **OFF**.
- SMS: **Always confirm** for voice-initiated messages until user changes setting.
- Pre-approved scheduled SMS: can send without daily confirmation **only if approved per routine and allowed by global safety settings**.
- Root controls: **OFF** until user enables individual capabilities.
- Unknown speaker: deny; offer repeat, no disclosure of private content.
- Missed recurring automation: default **SKIP late occurrence and keep future schedule**; user can override within explicit bounded grace period.
- Power-off and factory-reset-like operations: require high-risk confirmation; do not auto-run from learned patterns.

## Release milestones
M0 skeleton and test harness; M1 settings + offline ASR; M2 wake word/owner enrollment; M3 accessibility navigation and device actions; M4 contacts/SMS/calls; M5 generalized automations; M6 assistant-role/boot robustness; M7 optional Magisk bridge; M8 adaptive learning; M9 optimization, hardening and user acceptance.

## Open decision register (do not guess)
D-01 Verify OEM unlocking/root state and actual build ID before touching firmware. D-02 Which offline TTS voices are installed? D-03 Choose/test custom wake-word trainer and speaker-verifier license/model. D-04 Test selected assistant role's true hotword capabilities on Android 14. D-05 Confirm defaults for auto-messaging, especially during lock screen. D-06 Choose first-run theme from blue/purple mockups; the mockups are illustrative. D-07 Root-based unlock bypass is explicitly excluded.
