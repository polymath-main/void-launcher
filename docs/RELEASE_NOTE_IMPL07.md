# Release note — impl-07 config surface (area 07 of 7)

Branch: `feature/impl-07-config`. This is a feature branch; no merge to
`main` and no tag are part of this note — those are Shuvo's decisions.

## What changed for users

- **Settings button renamed honestly.** The old EXPORT CONFIG button saved
  the current config to the store; it is now labelled **SAVE TO STORE**.
  The real picker operations are **EXPORT FILE…** and **IMPORT FILE…**
  (SAF pickers) in the new "10. ADVANCED & FILE EXPORT" section, plus
  **RESTORE LAST BACKUP** (`config.json.bak`) and **IMPORT LEGACY FILES…**
  (routes to the area-06 SAF legacy import; it is not duplicated here).
- **Export warning.** Exporting writes the current config and toasts a
  warning: a config file can reveal directory paths and package names —
  share carefully. Import backs the store up first and reports validation
  warnings instead of silently applying a bad file.
- **More fields editable in Settings:** HUD/dock/omni font sizes (8–20sp),
  shell timeout (5–300s) and output cap (1 KiB–256 KiB), agent task
  timeout, wallpaper fit + status readout, notifications count-line toggle
  (default OFF, unchanged), plus persona/focus editor entry points and
  per-group reset previews.
- **Leaving Settings is safe now.** A Reset/group-reset preview is
  discarded unless you tap SAVE; a clean form does not rewrite the file;
  ordinary edits still instant-apply on leave. If the store file changed
  externally since Settings loaded it, SAVE asks "overwrite or reload"
  and leaving skips the overwrite with a toast, instead of silently
  overwriting your external edit.
- **Schema doc.** `docs/SCHEMA.md` lists every config field, its JSON key,
  type, default, and whether Settings exposes it — generated from code
  and guarded by a test, so it cannot rot silently again.

## Decisions recorded (not changed unilaterally)

- **G-06 legacy auto-read: KEPT, read-only.** The legacy config file
  auto-read stays as the migration path for that file only; it is never
  written back to the legacy path, and gating it would silently drop
  existing users' legacy configs on upgrade. The opt-in gate applies to
  NEW legacy reads (NodeOS card, bridge, SAF file import).
- **Release APK is debug-signed, not a production release.** The CI
  artifact is now labelled `void-launcher-v3.0.0-release-debug-signed`.
  Real signing needs Shuvo's keystore decision.
- **CI emulator smoke test is an open milestone, not done.** It was not
  wired in unverified; landing it needs one verified emulator run.

## Still needs Shuvo / device verification

Everything in the pipeline's open list: device/CI visual verification of
all Settings/SAF/dirty-check behaviour, the CI changes themselves (lint,
version check, checksums), and the standing product decisions ([GO]
button, two-pill identity, split dock label, STILL tokens, banner,
scrim slider, SAF workspace picker UX, WorkManager survival, shell cwd
move, durable agent scheduling, signing). JVM tests prove the pure seams
and source wiring only — they cannot prove rendered pixels or pickers.
