# Zeno — Revised UI design specification

**Target:** Pixel 4a 5G / Android 14. **UI:** Dark, Material 3-inspired.
**Intent:** Offline voice-operated phone with local learning and user-approved automation.

## Navigation
- Home: show wake-word standby or paused state, key shortcuts, upcoming automation.
- Commands: discover voice actions; teach new phrases.
- Routines: voice, scheduled, learned; create, approve, edit, pause, delete.
- Settings: voice enrollment, SMS policies, permissions, root, background listening, learning.

## Background and startup
- Always listening: ON by default after explicit setup/permission grant; visible pause switch.
- Start with Android: restore last user-selected preference; first-unlock constraints shown.
- Screen-off listening: target, not guaranteed on every firmware/build.
- Foreground status/Android microphone indicator must follow OS behavior.
- Standby stage should run a tiny wake-word detector, not full continuous speech recognition.

## Adaptive Learning
- OFF by default, opt-in. Minimum repetition default suggested as 5; user-configurable.
- Suggest-only and execute-approved modes. Learned activity strictly local.
- Observations scoped to granted permission: app usage, allowed device states, scheduling.
- No SMS-body learning by default. User can inspect and erase local history.
- No learned routine is silently promoted to execution.

## Scheduled SMS / example routine
- Name: Morning Greeting. Action: SMS to a saved contact (e.g., Mum).
- Schedule: every day 7:00 AM, local timezone.
- Text: editable, arbitrary user-dictated message.
- Exactly scoped pre-approval: contact + text + recurrence + consent.
- Use a once-per-occurrence idempotency key to avoid unintended duplicates.
- Offline speech/model processing; carrier network required for SMS dispatch.
- Expose success/error history and a missed-run handling option.
- Changes to recipient, message content, or schedule should trigger renewed approval.

## Security guardrails
- Speaker verification before actions (fallible). Sensitive actions use extra confirmation.
- PIN/fingerprint must remain in control of Android secure unlock.
- Allowlist root operations, no arbitrary free-form shell from dictated audio.
- User-accessible pause, disable learning, pause routines, and delete history.

## Static prototype
All screens are PNG visual mockups, not coded interactive layouts. The actual Android
implementation may require different control arrangements to meet Material 3 guidance.
