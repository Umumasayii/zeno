# 06 — Security, Root, Privacy & Permission Matrix

## Threat model
Assets: owner voice identity, SMS content and recipients, contacts, device control privileges, automation rules, history. Threats: borrowed/stolen unlocked phone, replayed voice, synthetic voice, confused deputy with malicious UI, arbitrary root execution, duplicate scheduled sends, privacy breach via logs/backups, permission revocation, malicious downloaded models, accidental action while locked.

## Trust boundaries
Raw mic input and ASR text are **untrusted**. Wake word and speaker verifier reduce errors but are not secure Android biometric. Only a typed, validated, separately authorized intent may reach execution. Android Keyguard still protects secure unlock. Magisk root can bypass many OS guardrails, so it needs the smallest possible API surface and careful user consent.

## Risk levels
- R0 informational: time, help, open public app; low-friction owner voice gate.
- R1 reversible control: flashlight, volume, home, scroll; verified owner voice, no extra confirmation by default.
- R2 external/private effects: send SMS, call, read private content, add routine; owner verification + configurable confirmation + lock policy.
- R3 destructive/privileged: shutdown/restart, delete data/routines, change security controls; separate explicit confirmation and possibly PIN/fingerprint where supported; no unattended learning inference.
- R4 disallowed in v1: direct voice-based secure Keyguard bypass, arbitrary root shell, automatic financial transfers, OTP/password entry, unknown-recipient messaging campaigns.

## Permissions matrix
| Capability | Platform permission/role | Conditions and fallback |
|---|---|---|
| Microphone | RECORD_AUDIO; assistant service | mic privacy switch and while-in-use/FGS rules apply; switch off releases resources |
| Default assistant | ROLE_ASSISTANT / VoiceInteractionService | user must select; test device support |
| Contacts | READ_CONTACTS | ask only on first contact feature use; manual number entry optional |
| SMS send | SEND_SMS and permitted default-handler role | require Android grant and legal app distribution policy; compose intent fallback needs user action |
| SMS read | READ_SMS / default SMS or Assistant role as allowed | decline gracefully if not authorized |
| Calls | CALL_PHONE; possibly default dialer for advanced call control | simple intent fallback |
| Notifications | NotificationListenerService grant | never expose private content if locked/unverified |
| App usage for learning | PACKAGE_USAGE_STATS via Usage Access | only after opt-in; do not pretend it sees everything |
| Screen navigation | AccessibilityService explicit user enablement | no secret interaction/credential fields; respect protected windows |
| Screenshot | Accessibility action / media APIs | secure/DRM windows may block capture |
| DND | Notification Policy Access | grant only when feature used |
| Alarm precision | SCHEDULE_EXACT_ALARM special access if eligible | check canScheduleExactAlarms; inexact fallback with warning |
| System brightness/settings | WRITE_SETTINGS special access in some paths | request only if needed; prefer system intent |
| Root actions | Magisk `su` grant | user must independently grant; privilege can be revoked |
| Battery optimization | request appropriate allowance only if justified | no assumption root fixes runtime constraints |

## Confirmations
A pre-approved **scheduled** SMS routine may run without daily spoken confirmation only when the user approved exact recipient/content/schedule and enabled automatic execution; keep explicit log, pause and one-tap revoke. Voice-initiated SMS follows the user-controlled `Immediate / Always confirm / Smart` mode. Do not use speech confidence to overrule policy.

## Lock screen
Supported: listen when permitted, wake screen, perform allowed actions, and request Android dismissal. `KeyguardManager` can request dismissal only when Android policy permits; otherwise the system asks for PIN/fingerprint. Do not use root to remove lock credentials, type PINs, suppress security dialogs or claim voice is a trusted biometric. Read sensitive messages aloud only after policy permits and owner verification is strong; for high privacy require device unlock.

## Root execution design
Use a narrow interface with e.g. enum `POWER_OFF`, `REBOOT`, `SET_SPECIFIC_ALLOWED_PROPERTY` (where safe); no arbitrary command string. A hardcoded audited mapping executes only after PolicyEngine approval and Magisk grant. No parser output can enter shell command arguments. Run with timeouts and report failure; never leave broad root daemon exposed through exported Android components. Do not auto-root or manipulate boot images from inside the app.

## Spoofing and owner voice
Enroll owner with sufficient varied samples; save encrypted speaker embeddings. Collect non-owner negative samples only with separate consent. Tune equal error rate/FAR/FRR on this device; anti-replay and challenge phrase may help but cannot guarantee identity. For high-risk actions require on-device PIN/fingerprint credential flow regardless of voice profile.

## Data security
Local-first and user-readable storage retention. Avoid raw speech logging. Explicit consent for crash logs; redact messages/contacts; no embedded API keys. Encrypt backups and test restores. Never expose ContentProviders/Activities with high-risk actions to arbitrary apps. `android:exported` carefully configured, pending intents immutable when possible. Automatic Git ignore for `*.keystore`, logs, private profile files, backups. Android root increases attack surface; clearly display risk.

## Distribution and regulatory distinction
Personal sideloaded APK still subject to Android permission and privacy model. A Google Play version is subject to SMS/Call Log restricted-permission policy, including qualifying default handler or approved exception. Do not assume private sideload permissions predict Play approval. Verify up-to-date policy before publishing.

## Rooting warning
Root/unlock is a separate manual owner decision; bootloader unlocking wipes data and may compromise banking apps. This architecture package contains **no instruction to bypass OEM bootloader eligibility**. Never root or flash firmware autonomously from Claude Code.
