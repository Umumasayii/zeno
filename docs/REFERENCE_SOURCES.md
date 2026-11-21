# Reference sources — checked 2026-11-21

These are design constraints, **not a guarantee that every API works on every Pixel build**. Claude should recheck latest documentation before shipping.

1. Android `VoiceInteractionService` selected voice assistant service: https://developer.android.com/reference/android/service/voice/VoiceInteractionService
2. Android 14+ microphone foreground service restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
3. Boot-completed microphone FGS restriction: https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed
4. Direct Boot storage and first unlock: https://developer.android.com/privacy-and-security/direct-boot
5. Android 14 exact alarms: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
6. Android AccessibilityService supported global actions: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
7. Android on-device SpeechRecognizer availability: https://developer.android.com/reference/android/speech/SpeechRecognizer
8. Android default handlers and permissions: https://developer.android.com/guide/topics/permissions/default-handlers
9. Google Play restricted SMS and Call Log: https://support.google.com/googleplay/android-developer/answer/10208820
10. UsageStatsManager event access and limits: https://developer.android.com/reference/android/app/usage/UsageStatsManager
11. Vosk offline ASR: https://github.com/alphacep/vosk-api
12. Vosk model sizes/licensing: https://github.com/alphacep/vosk-space/blob/master/models.md
13. Pixel factory images (firmware-specific, use only after verifying actual build): https://developers.google.com/android/images
14. Material Design 3: https://m3.material.io/

**Correction/nuance:** SMS role/permission handling differs for sideloading versus Google Play policy. Device-only local ASR may be unavailable on the phone until a service/model is installed. `VoiceInteractionService` persistent-process documentation is not a promise of unrestricted continuous mic access before first unlock. Root cannot make voice a secure Android biometric.
