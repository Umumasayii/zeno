# 04 — General-Purpose Automation Engine

## Requirement
Zeno must support many arbitrarily user-created automations, limited by practical storage and battery (not an artificial limit of one SMS or a handful of routines). **Every** user routine can have a name, description, trigger(s), conditions, action sequence, approval, enabled state, execution policy, pause/delete/edit/duplicate, execution history, notification and version.

## Builder UX
Step 1 name → Step 2 choose trigger → Step 3 optional conditions → Step 4 choose one or more actions → Step 5 timing/retry/missed-run options → Step 6 sensitive-action review → Step 7 approve and enable. Search + filters `All/Active/Paused/Learned/Failed`, sorting, bulk enable/disable, import/export.

## Trigger catalog and Android feasibility
| Type | Parameters | Android mechanism and limitation |
|---|---|---|
| Time-of-day | zone, clock time, weekdays/rrule | AlarmManager; exact permission may be denied; fallback announced |
| Date | local date/time | AlarmManager/WorkManager based on precision |
| Interval | every N minutes/hours | WorkManager inexact with minimum periodic interval, alarm when eligible |
| Charging/battery | charging, percentage crossing | broadcast/battery observers; debounce/hysteresis |
| Boot/first unlock | boot completed, user unlocked | Direct Boot and BOOT_COMPLETED receivers; respect restricted data availability |
| App foreground | selected app opens/closes | UsageStatsManager with permission, event recency/latency limits |
| Screen | unlocked/locked/on/off | broadcast/activity state subject to Android limits |
| Headset/connectivity | headphones/Bluetooth/Wi-Fi | APIs/events requiring appropriate permissions; some radios restricted |
| Notification received | app/source/label | NotificationListenerService with explicit notification access |
| Voice phrase | custom supported phrase | via owner-verified voice command, not unattended time trigger |
| Learned pattern | suggestion created | **Not a trigger for automatic execution without approval** |

## Conditions
Support `all`, `any`, `not`, depth≤3 initially, max step/condition count set by performance measurement, no silent truncation. Examples: charging AND battery<40; weekdays AND home Wi-Fi (if authorized); screen unlocked; recipient accessible; permission granted. Evaluate conditions at execution time; if false skip and log `SKIPPED_CONDITION`.

## Action catalog
- Low risk: open app, set volume/brightness, flashlight, say phrase, notify, play/pause media, enable DND (permission required), lock screen.
- Medium: send approved SMS to approved recipient, start call, navigate app, read notification into spoken response (especially locked).
- High: reboot/shutdown, change security/system policy, mass file operations; often **not eligible for unattended automations**.
- Explicitly unsupported: arbitrary rooted shell string, OTP extraction, banking login automation, financial transfers inferred from habits, stealth recording, security bypass.

## Routine JSON contract (see schema)
`id`, `name`, `enabled`, `origin` (`manual`/`learned`), `version`, `trigger`, `conditions`, `steps`, `executionPolicy`, `approval` and `createdAt`; see `specs/automation.schema.json` for syntax. Sample routines are **not activated** merely by import.

## Scheduling logic
1. Validate legal trigger + permissions + action + approval; schedule next occurrence using local timezone and DST policy.
2. On trigger delivery, transactionally create a unique Run with `occurrenceId`, version and idempotency key. Refuse duplicate occurrence insert.
3. Re-evaluate conditions, role availability, lock state, permission state, and approval version.
4. Execute ordered typed actions; each step state `PENDING / RUNNING / SUCCEEDED / FAILED / UNKNOWN / SKIPPED`.
5. Record remote/carrier callbacks separately; `UNKNOWN` outcome means **never blindly resend SMS**.
6. Notify on important failures if enabled; reschedule next occurrence and reconcile any pending work after reboot.

## Exactly-once boundary
The application can guarantee **at most one local send attempt per occurrence** with database transactions/idempotency. It cannot guarantee exactly-once delivery through a carrier when process death or network uncertainty occurs; prevent blind automatic resend of an `UNKNOWN` status. Reboot/timezone edits do not resurrect completed occurrences.

## Missed-run and duplicate policies
Default recurring schedule: skip a missed past occurrence beyond small grace window (proposed 5 minutes) and schedule the next; allow `run-next-unlock` only when user explicitly chooses. Never send several missed SMS messages in a catch-up burst. WorkManager periodic jobs are not precise wall-clock guarantees.

## Approval model
Approval binds **routine ID + version + exact side-effecting action + target + schedule + allowed state**. Edits to recipient, message, action class, schedule frequency or privileges invalidate approval. Global override can disable all automation execution instantly. SMS policy and pre-approved routine permission are separate; daily confirmation may be skipped only under explicit per-routine preapproval and global policy.

## Failure recovery
Phone off: no actions run. First unlock after reboot: load credential-protected schedules and reconcile. SIM missing / no network: mark failed/unavailable, never claim sent. Exact alarm permission revoked: degrade visibly and notify once. Permission revoked: automatically pause affected routines. Contact deleted: pause and ask owner to resolve. App UI changed: fail safely rather than random tapping. Rate limit repeated failures and alerts.

## Editing/import/export
Edits create new version; keep read-only past version for audit. Exports omit personal recipient numbers and secret values unless owner explicitly opts for an encrypted backup. Schema migration on future releases. Automation library includes a dry-run preview and a one-shot `Run now` test that still enforces authorization.

## Examples
`specs/examples/*.json` contains different kinds of generic routines: family greeting (illustration), study mode, battery saver, bedtime, app shortcut, missed-charge reminder and notification reading. See `docs/06_SECURITY_PERMISSIONS.md` before executing examples.
