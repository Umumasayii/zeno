# Zeno

A private, offline-first voice assistant for one owner's Google Pixel 4a 5G (Android 14), written in Kotlin with Jetpack Compose and Material 3.

> **Status: Milestone 0 (skeleton, contracts, tests).** Zeno does not listen, recognise speech or control the phone yet. See [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md) for what is actually verified.

## Principles
- No cloud, analytics, telemetry or backend. The app has **no INTERNET permission** (a test enforces this).
- Speech is untrusted input. Every request becomes a typed intent → policy check → action. There is no text-to-shell path.
- Root (Magisk) is optional, and limited to a fixed enum of operations.
- Learning is OFF by default and never authorizes itself.
- Voice is not Android authentication: unlocking always uses PIN, pattern or fingerprint.

## Repository layout
| Path | What |
|---|---|
| `app/` | Compose app (M0: four tabs with honest placeholder screens) |
| `core/domain/` | Pure-Kotlin contracts: intents, policy engine, voice state machine, action router, root allowlist, routine model, scheduling |
| `core/testing/` | Test fakes |
| `spike/` | Separate debug APK for Android 14 device feasibility spikes |
| `docs/`, `specs/`, `tests/` | Requirements, architecture, ADRs, spikes, fixtures, test plan |
| `UI Designs/` | Visual references (not functional proof) |

Start with [`README_START_HERE.md`](README_START_HERE.md) and [`CLAUDE.md`](CLAUDE.md).

## Build and test (Windows, PowerShell)
Requires Android Studio (its bundled JDK) and Android SDK platform 37.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test :app:assembleDebug :spike:assembleDebug
```

Install on the phone over USB with `adb install -r app\build\outputs\apk\debug\app-debug.apk`. Use `adb install` rather than opening the APK on the phone: Android restricts some permissions for apps installed from files (see docs/11, R4).
