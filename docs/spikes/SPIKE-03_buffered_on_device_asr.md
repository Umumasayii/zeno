# SPIKE-03 — On-device recognizer with audio Zeno already captured

**Status:** CODE READY, **not run** (phone unavailable). Requirements: Z-002, Z-005, Z-028; ADR-008, ADR-009.

## Why
The M2b wake pipeline (`WakePipeline`) captures the wake phrase plus the command, verifies the owner on that audio, and only then hands it on. Nothing from a non-owner is ever transcribed. That needs a recognizer that accepts **audio we pass in**, not only the live microphone.

Android 13+ has `RecognizerIntent.EXTRA_AUDIO_SOURCE` (a `ParcelFileDescriptor`) with `EXTRA_AUDIO_SOURCE_CHANNEL_COUNT`, `_ENCODING` and `_SAMPLING_RATE`. It is not documented whether the Pixel's **on-device** recognizer (`createOnDeviceSpeechRecognizer`) honours it.

## Questions
1. Which offline languages are installed (`checkRecognitionSupport`)?
2. Does the on-device recognizer return a transcript for 4 s of 16 kHz mono PCM16 passed through a pipe? With what latency?
3. Does it still work in **airplane mode**?
4. Does any error come back (e.g. `ERROR_LANGUAGE_UNAVAILABLE` 13, `ERROR_CANNOT_CHECK_SUPPORT` 14, client error 5)?

## Procedure (owner runs; audio stays in memory, the log holds numbers only)
1. Install the spike: `gradlew.bat :spike:assembleDebug`, then `adb install -r spike\build\outputs\apk\debug\spike-debug.apk`.
2. Open **Zeno Spike** → *1. Grant microphone + notifications* (if not granted already).
3. *4a. Log offline languages*. Expect `en-US` in `installed=`; if it's missing, install the offline English pack in Android Settings first.
4. Turn on **airplane mode** (Wi-Fi off too).
5. *4b. Record 4 s, then transcribe* and say "hey Zeno, open the camera". The transcript appears on screen only. Repeat 5 times, including once from about 1 m away.
6. Export the log: `adb shell run-as com.zeno.spike cat /data/user_de/0/com.zeno.spike/files/spike_log.txt > spike-results\spike03.txt` (git-ignored).

## Results
| # | Measurement | Result | Evidence |
|---|---|---|---|
| 1 | Installed offline languages | NOT RUN | |
| 2 | Transcript from piped audio (5 tries): words correct / latency | NOT RUN | |
| 3 | Works in airplane mode | NOT RUN | |
| 4 | Errors seen | NOT RUN | |

## Decision rule
- **Works offline:** the pipeline transcribes the verified buffer (preferred: nothing unverified is ever transcribed, no second microphone session).
- **Fails:** after the owner is verified on the wake phrase plus a short capture, the app hands the microphone to `createOnDeviceSpeechRecognizer` for the command, as push-to-talk does. A bundled ASR from the same engine family (ADR-009) is the fallback if verification on the wake phrase alone proves too weak.
