# Windows project layout

```text
C:\Users\branc\Desktop\BDIM\CODING FOR iMPACT\Zeno\
├── CLAUDE.md                     ← Claude Code instructions, must be at project root
├── README_START_HERE.md          ← Setup/read order
├── docs\
│   ├── 01_PRODUCT_REQUIREMENTS.md
│   ├── 02_SYSTEM_ARCHITECTURE.md
│   ├── 03_VOICE_COMMANDS.md
│   ├── 04_AUTOMATION_ENGINE.md
│   ├── 05_ADAPTIVE_LEARNING.md
│   ├── 06_SECURITY_PERMISSIONS.md
│   ├── 07_UI_UX_SPECIFICATIONS.md
│   ├── 08_LOCAL_AI_MODELS.md
│   ├── 09_TESTING_PLAN.md
│   ├── 10_DEVELOPMENT_ROADMAP.md
│   ├── REFERENCE_SOURCES.md
│   ├── IMPLEMENTATION_STATUS.md
│   ├── decisions\ADR-001.md ... ADR-005.md
│   └── diagrams\*.mmd
├── specs\
│   ├── voice_command_examples.csv
│   ├── automation.schema.json
│   ├── requirement_traceability.csv
│   └── examples\*.json          ← Documentation-only, disabled by default
├── tests\
│   ├── TEST_CASES.csv
│   └── DEVICE_RESULTS_TEMPLATE.md
├── prompts\
│   ├── START_CLAUDE_CODE.md
│   └── FEATURE_TASK_TEMPLATE.md
└── UI Designs\
    ├── Screens\01_Welcome.png ... 31_Listening_Paused.png
    ├── Concept References\
    ├── Zeno_UI_Overview.png
    └── IMPLEMENTATION_NOTE.md
```

When Claude creates Kotlin source, put Android code and Gradle files **at this same project root**; do not make a nested `Zeno/Zeno` directory. Avoid moving docs into app resources. Keep real enrollment files, live contacts/SMS, APK signing secrets and backups outside Git.
