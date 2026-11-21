# Zeno — project instructions for Claude Code

## Mission
Build Zeno, a **private, offline-first, low-power, owner-voice-controlled Android assistant** for Google Pixel 4a 5G (`bramble`), Android 14. The user plans optional Magisk root; root is NOT a prerequisite for most features. The assistant acts on the user's phone and runs previously approved automations.

## READ FIRST before modifying code
1. `README_START_HERE.md`
2. `docs/01_PRODUCT_REQUIREMENTS.md`
3. `docs/02_SYSTEM_ARCHITECTURE.md`
4. `docs/06_SECURITY_PERMISSIONS.md`
5. `docs/10_DEVELOPMENT_ROADMAP.md`
6. `docs/IMPLEMENTATION_STATUS.md`
Then consult feature docs, `specs/`, `tests/`, and corresponding `UI Designs/Screens/*.png`.

## Absolute constraints
- **No cloud processing, analytics SDK, telemetry, ad SDK, backend, or remote command channel.** Networking may be required by OS/carrier/other apps, but Zeno must not upload voice samples, transcripts, contacts or learning logs.
- Keep voice models on device; make wake-word and language packs replaceable modules, and test actual offline speech recognition.
- Always-on wake-word listening while enabled, **best effort under Android 14**. Never promise 100% uptime or covertly defeat OS privacy controls. `VoiceInteractionService`/assistant role is preferred; test boot, first-unlock and screen-off states.
- **Voice is not secure Android authentication.** Never silently bypass Keyguard/PIN/fingerprint using root. Voice controls permitted lock-screen tasks only; secure unlock uses Android authentication.
- Owner verification required for normal voice commands; add replay/synthetic-voice defenses and measurable false accepts, not a claim of perfect identity.
- Root actions use a small **typed allowlist**; forbid executing arbitrary transcripts, file paths, shell fragments or generated commands as root. Require per-capability enablement and auditing.
- User-created automations have independent triggers, conditions, steps, pause/enable, versioning, approvals, deduplication, retries and execution logs. The Mum/7:00 AM message is only a fixture. Do not hardcode it or imply any fixed automation limit.
- Adaptive learning must be disabled by default. Never turn raw observed habits into silently executable actions. Approval creates an explicit routine version; updated routines require reapproval for new sensitive privileges.
- SMS sending policy must be configurable: immediate / always-confirm / risk-adaptive. **Pre-approved scheduled messages** may execute without daily confirmation when separately authorized; show recipient, content, costs and execution history.
- No privileged action without user-approved intent; destructive actions and payment-related operations need additional checks. Do not automate OTP/credential entry or transfers of money by inference.
- UI uses Kotlin + Jetpack Compose + Material 3. Source of truth is `docs/07_UI_UX_SPECIFICATIONS.md`; PNG images are visual references, not functional proof.
- Prefer testable ports/adapters and interfaces. Implement normal Android API routes before using accessibility and root fallbacks.

## Development behavior
- First **inspect existing repository** and summarize what is there. Never overwrite valuable code or documents without showing the diff.
- Write a short implementation plan with affected modules, requirement IDs, permission implications and tests BEFORE coding. Ask when an essential unsafe decision is unresolved.
- Implement the next roadmap milestone only; do not jump straight to rooting or SMS transmission.
- Each new command requires parser fixtures, authorization policy, error handling and tests.
- Every scheduler action requires idempotency key, outcome states, missed-schedule policy, timezone/DST tests, reboot tests and cancellation tests.
- Avoid UI actions that assume screen coordinates; use AccessibilityNodeInfo semantics with robust fallback and user visibility.
- Check Android and Google Play restrictions (especially SMS/Call Log, microphone, accessibility, exact alarms). Personal sideloading and Play Store publication are different distribution scenarios.
- If an Android 14/device-specific mechanism is uncertain, add a `SPIKE` with a reproducible test, not a fabricated implementation.
- Use only official distributions of tools/models and verify licenses. Do not bundle copyrighted TTS/ML weights without appropriate license.
- Keep secrets, real contacts, messages, local backup and voice enrollment files out of Git. Use synthetic fixtures.
- Update `docs/IMPLEMENTATION_STATUS.md` and related decision records each milestone. Mark Complete only with tests passing on device.

## Working definition of done
- Compiles reproducibly for the chosen min/target SDK; unit and instrumentation tests pass.
- Real Pixel 4a 5G / Android 14 acceptance tests, negative tests and permission-revocation tests pass.
- Works fully offline in airplane mode for applicable tasks; explicitly reports that SMS/calls cannot use the carrier while offline.
- No unapproved root/scheduled action, duplicate SMS, voice-only secure unlock, or unnecessary background recording.
- User can switch listening off, disable learning, inspect/erase local data and pause/delete automations.

## Session startup
Read `docs/IMPLEMENTATION_STATUS.md` and take the *next unchecked milestone*. If everything is TODO, start with Milestone 0: compile-only project skeleton, architecture contracts, fixtures and test setup. Never assume a spec is already implemented.
