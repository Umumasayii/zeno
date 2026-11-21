# 05 — Adaptive Learning & Autonomous Actions

## Principle
Learning should be **useful, transparent and opt-in**, not a secret observer or a self-authorizing agent. Distinguish (a) observation, (b) proposal, (c) user approval, and (d) scheduled execution. These are separate states with explicit UI.

## Settings
- Top-level `Adaptive Learning`: **OFF** by default; requires informed, user-facing toggle.
- `Suggestions only` vs `Execute my approved learned routines`.
- `Minimum repetitions`: default proposed 5; configurable, not enough by itself to prove a habit.
- `Learning sources`: app open/close events, Zeno command types, explicit device-setting changes where observable, clock/battery/charging state. Each source opt-in, accessible via Android permissions.
- `Retention`: proposed 30 days raw *minimal summaries*; aggregate older data; clear history and disable each source separately.
- `Show suggestion notifications`: yes/no; `reviewed/ignored` proposals list.

## Observation boundaries
Record only approved, minimal events: category of action, rounded time bucket, day-of-week, charging state, app package identifier (if opted in), optionally normalized Zeno command intent. Do **not** learn from raw microphone audio, full SMS contents, contacts, keystrokes, clipboard, authentication, screen contents, banking apps or passwords. Avoid sensitive-category apps by default. Root status does NOT justify unlimited collection.

## Learning pipeline
1. Consent gate on every event source.
2. Normalize event and apply sensitive-source exclusion; perform local aggregation.
3. Detect repeated time/action sequence using interpretable rules (e.g. ≥5 occurrences on ≥4 distinct days, stable time window, acceptable confidence).
4. Construct a *candidate* with confidence, evidence count and explanation.
5. Suppress duplicate suggestions and respect `Never suggest this`.
6. Show proposal with precise trigger, conditions, action sequence, data needed and intended effect.
7. User chooses `Approve`, `Edit before approving`, `Dismiss` or `Never suggest`.
8. Approval creates a disabled routine or, with explicit `Enable` selection, an approved enabled routine. Never auto-enable a new candidate.
9. If context changes, evaluate/cancel existing approved routine; do not silently alter its approved side effects.

## Confidence and pattern criteria (starting heuristics, subject to validation)
- Single action repeat: ≥5 times over ≥4 days within 30-day lookback; median wall-clock deviation ≤30 min; avoid weak time-only correlations.
- Sequence: ≥4 repeated sessions with same ordered actions, each within configurable temporal window (e.g. 15 min), never include credential-related steps.
- Trigger choice: only if app truly has an observable signal; some phone interactions are invisible without access, so Zeno may not be able to learn them.
- Quality bar: report suggestion precision (approved/total shown), false prompts per week, and battery overhead. No claim of "AI knows your life".

## Approved autonomy
Learning-enabled does **not** mean unrestricted autonomy. New behaviors → proposal. Existing approved routine → follow normal automation engine after trigger and conditions. Disabling learning stops collection and new suggestions; it does not necessarily pause previously approved automations unless user chooses `Pause learned automations as well` (show this choice explicitly).

## High-risk exclusions
Never infer and autonomously execute SMS to new recipients, money movements, deleting data, changing lock policies, root shell, security credentials, or disabling phone protections. An SMS candidate may suggest a template; execution requires user to review exact contact, message and recurrence. Sensitive automations are independently vetted by PolicyEngine.

## Explainability UI
Each proposal says `Suggested because you [action category] on [N distinct days] near [time]`; links to privacy controls and observed event summaries. No false certainty. Suggestions can be edited or deleted permanently. Show what will run, where and why.

## Cleanup and privacy tests
Disabling learning must immediately stop observation tasks; deleting history removes derived candidates/summaries; resetting voice enrollment removes speaker templates. Verify after app restart and reboot. No hidden background learning when settings show OFF.
