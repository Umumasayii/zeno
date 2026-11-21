# Installing offline models (owner-side, sideload only)

Zeno has no INTERNET permission and never downloads anything. You copy model files to the phone yourself. Zeno uses a model only if its **size and SHA-256** match the manifest and the entry names a **licence and source** (docs/08).

## Location
```
/sdcard/Android/data/com.zeno.assistant/files/models/          (release build)
/sdcard/Android/data/com.zeno.assistant.debug/files/models/    (debug build)
```
The directory is created the first time the app runs. The exact path is shown on **Settings → Permissions & status → Offline models**.

## manifest.json (version 1)
```json
{
  "version": 1,
  "models": [
    {
      "id": "wake-hey-zeno",
      "kind": "wake_word",
      "version": "1.0",
      "file": "hey_zeno.onnx",
      "sha256": "<64 lowercase hex characters>",
      "sizeBytes": 123456,
      "license": "<SPDX id or licence name, as stated by the official release>",
      "source": "<official release URL or project name>",
      "phrases": ["Hey Zeno"]
    }
  ]
}
```
- `kind`: `wake_word`, `asr`, `speaker_verification` or `tts`.
- `file`: a plain file name in the same folder. Paths (`/`, `\`, `..`) are rejected.
- `phrases`: required for `wake_word`; it is the list of phrases that model can actually detect (Z-001).
- Unknown fields, a different `version`, or a manifest over 256 KB make the whole manifest invalid. A single bad entry is listed as **Rejected** with the reason; the others still load.

## Copying from a computer
```powershell
adb push manifest.json /sdcard/Android/data/com.zeno.assistant.debug/files/models/
adb push hey_zeno.onnx /sdcard/Android/data/com.zeno.assistant.debug/files/models/
```
Compute the hash with `Get-FileHash -Algorithm SHA256 hey_zeno.onnx` (PowerShell shows it in upper case; write it in lower case). Then tap **Check again** on the status screen.

## What M1 does with models
M1 only **checks** them (missing, wrong size, checksum mismatch, rejected). No engine loads them yet: wake word arrives in M2b, speech recognition and voice replies in M2a. Do not commit model files to Git (`.gitignore` excludes `models/`, `*.onnx`, `*.tflite`).
