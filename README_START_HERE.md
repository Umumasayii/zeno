# Zeno — offline Android voice assistant | Claude Code handoff

**Document version:** 2.0 planning baseline · **Date:** 2026-11-21 · **Target:** personal Pixel 4a 5G (`bramble`) running Android 14 (API 34).

## What is included

This is a requirements and design package, **not a compiled Android app or implemented security mechanism**. It includes 31 existing UI reference PNGs, product requirements, engineering architecture, hundreds of command utterance examples, general automation schema/examples, an adaptive learning design, local model options, permissions and threat model, UI acceptance details, test checklist, phased development backlog and a ready-to-use `CLAUDE.md`.

## Put this package in the correct folder

1. Open `C:\Users\branc\Desktop\BDIM\CODING FOR iMPACT\Zeno` on Windows.
2. **Extract the ZIP contents directly into that existing `Zeno` folder** (not into a second `Zeno` subfolder). You should see `CLAUDE.md` at the root, alongside `docs`, `specs`, `tests`, `prompts`, and `UI Designs`.
3. Back up any edited files first. The `UI Designs` folder in this package contains the 31 mockups from our prior updated package. The docs here supersede prior v1 architecture documents.
4. In a terminal run `cd "C:\Users\branc\Desktop\BDIM\CODING FOR iMPACT\Zeno"` (PowerShell), then launch `claude` from **inside this project root**. Claude Code normally reads project-level `CLAUDE.md` in its current hierarchy.
5. Give Claude the first task in `prompts/START_CLAUDE_CODE.md`. Ask it to inspect and plan before creating code.

## Read in this order

1. `CLAUDE.md` — non-negotiable project behavior and how to work.
2. `docs/01_PRODUCT_REQUIREMENTS.md` — product scope and acceptance criteria.
3. `docs/02_SYSTEM_ARCHITECTURE.md` and `docs/10_DEVELOPMENT_ROADMAP.md` — design and build order.
4. `docs/06_SECURITY_PERMISSIONS.md` — safety boundaries for root, messages and lock screen.
5. The relevant feature spec and its tests before implementing each feature.

## Scope: three independent pillars

- **On-demand voice:** Wake word → owner verification → offline understanding → permission/policy checks → device actions.
- **User-created automations:** Unlimited within practical device resources; many kinds of trigger, condition, sequence and per-routine control, not only SMS examples.
- **Opt-in learning:** When enabled, observe eligible local patterns, suggest routines, and run **only explicitly approved** learned routines.

## Documentation status

- **Approved goals** are recorded as `REQ` identifiers.
- **Design proposals** are identified as such; they are not user-confirmed facts.
- **Unverified/device-dependent** capabilities are marked `SPIKE` and must be tested on the actual Pixel before being promised as working.
- Example contacts such as Mum/John and 7:00 AM schedules are illustrations, not real contacts, tasks or preinstalled automations.

## Offline meaning

All **voice analysis, profiles, rules, planning, logs, settings and saved automations** operate locally with no backend. Carrier SMS/calls still need a mobile network; other apps like WhatsApp may require their own internet access. Model installation or updates can be done through USB sideloading; core use must not call cloud APIs.

## How to keep Claude aligned

One feature at a time. Do not tell Claude to implement everything in one pass. Require each feature PR to include its requirement IDs, tests, screenshots if UI changed, and updated `docs/IMPLEMENTATION_STATUS.md`. Never count a UI mockup or design file as an implemented feature.
