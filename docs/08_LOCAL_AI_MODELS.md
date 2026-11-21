# 08 — Local AI Models and Data Plan

## Pipeline / critical separation
Hotword detector → speaker verifier → offline ASR → typed intent parser → dialogue/policy/action. **Wake-word detection is not voice verification**, and an ASR transcript is not an authenticated command.

## Candidate technologies (benchmark before selection)
| Task | Candidate | Advantages | Caveats |
|---|---|---|---|
| Wake word | Porcupine or custom TFLite hotword network | Low-power keyword spotting | Licensing, runtime availability and truly custom names must be checked; do not assert arbitrary typed wake word works |
| Offline English ASR | Vosk `vosk-model-small-en-us-0.15` | About 40 MB model; documented small mobile use | Around 300 MB working RAM according to project; accuracy depends on accent/noise |
| Alternate on-device ASR | Android `SpeechRecognizer.createOnDeviceSpeechRecognizer` | OS-integrated when available | Installed local service/models vary; must check `isOnDeviceRecognitionAvailable` and no network path |
| Speaker verification | Small locally licensed TFLite/ONNX speaker embedding encoder | Owner enrollment + cosine/similarity gate | Must benchmark false accepts, different noise and playback attacks; no guaranteed identity |
| Anti-spoof | Local audio replay detection + challenge phrase for sensitive actions | Raises replay difficulty | No guaranteed spoof proof; performance tradeoff |
| Command parser | Local keyword/grammar + bounded slot extraction | Small, fast, interpretable | May need many templates for natural language |
| Advanced interpretation | Optional quantized small local NLU/LLM (later) | Flexible queries | Large battery/RAM cost; output constrained to typed schema and policy gate |
| Speech output | Android offline TTS voices | Local output when engine/voice installed | Verify offline-capable voice pack; missing voice fallback |

## Proposed model measurement protocol
- Offline assertion: Airplane mode, disable Wi-Fi, show recognition works without requests, validate package network activity.
- Memory profile: cold vs steady-state RSS/PSS for standby detector, ASR active, speaker verification active.
- Battery: compare 8-hour screen-off overnight idle for Zeno disabled vs standby on, same network state and temperature.
- Accuracy: custom wake-word in silence, TV audio, outdoors; enrolled owner near/far; 5+ unknown speakers with consent; recorded playback; noisy English; varied accents.
- False-accept rate and false-reject rate; logs store **metrics without retaining the actual captured voice**.
- Time to respond 50th/95th percentiles from utterance completion to first action.

## Voice enrollment design
Enrollment records multiple samples over varied phrases and distances with VAD/quality gating; do not require a rigid number until tests. Create aggregate embedding with threshold calibrated to actual owner and negatives. Keep embeddings encrypted locally, offer complete retrain/reset, never share or use training audio outside device. Sensitive root/personal operations can still require Android auth.

## Custom names
UI display name can be any text. A working custom wake word requires a compatible keyword model; if model generation is unavailable locally, show a set of supported wake words or require an explicitly consented offline training step. The assistant's spoken name is not proof the hotword model recognizes it.

## Language plan
Start English only. Kinyarwanda is a **future investigation**; check licenses, actual available mobile models, speech output, and benchmark quality before promising. Do not reuse cloud translation under an offline label.

## Data inventory
| Data | Storage | Default retention / action |
|---|---|---|
| Speaker embeddings | Encrypted app storage, Keystore key | Until owner deletes / re-enrolls |
| Raw voice recordings | Memory only during active processing | Do not persist by default |
| User preferences | DataStore | Until reset/export |
| Routines and approvals | Room | Until owner deletes |
| Execution logs | Room | Proposed 30-day adjustable retention, redact SMS contents |
| Learning summaries | Room; enabled only with consent | Proposed 30-day window, delete on request |
| Model binaries | App assets/private model dir | Checksum/manifest/license, replaceable locally |
| Diagnostics | Redacted local app storage | OFF by default; manually export with consent |

## Model license and security work
Before use, document model URL, owner, license, checksum, size, runtime memory, provenance, export controls if any. Never distribute unlicensed model weights. Verify AAR/Gradle artifact versions at implementation time, not copied from this plan. Prefer no internet permission in main app when practical; if updates require network, isolate optional update path.
