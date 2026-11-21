ZENO — OFFLINE VOICE ASSISTANT | UPDATED UI DESIGN PACKAGE
=========================================================
Target: Google Pixel 4a 5G with Android 14 (bramble).
Theme: dark Material-inspired Pixel UI, indigo/lavender and blue accents.
Screens: 31 individual 1080 x 2340 PNG mockups.
All images are static references, not an interactive or implemented app.

WHAT CHANGED IN THIS REVISION
- Updated Home: Always Listening, Pause, and next scheduled action.
- Updated Settings: Background & Startup and Learning & Automation first.
- Updated Routines: voice-triggered, manually scheduled, and learned.
- Updated Offline & Battery: low-power hotword standby model.
- Added 13 screens covering learning, recurring SMS, approvals, startup and privacy.

IMPORTANT BEHAVIOR / TECHNICAL RESTRICTIONS
- Learning starts OFF. Suggestions require review and explicit approval.
- Previously approved scheduled actions can work with learning disabled.
- Automatic SMS must be approved for the exact contact, content and schedule.
- Sensitive or destructive root actions require a separate authorization policy.
- Voice recognition cannot guarantee exclusive recognition of one speaker;
  replay/synthetic voices may still fool the model.
- Voice alone cannot replace PIN/fingerprint when Android requires secure unlock.
- On Android 14, microphone service startup is restricted; a selected default
  assistant and a lightweight detector are the intended design.
- Zeno should restore listening preference at boot, but voice may only become
  available after the first unlock, and OS privacy controls always prevail.
- Scheduled carrier SMS requires network service even when Zeno's models are offline.
- For scheduled sends, prevent accidental duplicate sends after retries and
  avoid late delivery of stale reminders without explicit user policy.

FILES
Screens/: 31 numbered screens; filenames match SCREEN_INDEX.csv.
Concept References/: high-level visual drafts from the planning discussion.
Zeno_UI_Overview.png: compact visual overview of all screens.
DESIGN_SPEC.md: controls, states and implementation references.
SCREEN_INDEX.csv: full list of mockups.

To save on your Windows computer, unzip the top-level 'UI Designs' folder at:
C:\Users\branc\Desktop\BDIM\CODING FOR iMPACT\Zeno\
This ZIP contains no runnable application or fonts.
