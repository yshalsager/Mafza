# Mafza Internal Release Checklist

Source of truth: `docs/PLAN.md` section `Release Acceptance Checklist (Must Pass Before Internal Release)`.

- [x] Live run from all triggers with full preflight green.
- [x] Dry Run from in-app action with zero external side effects.
- [x] Message-app provider selection, test-provider flow, unavailable-provider fallback.
- [x] Intent action step configuration and execution (resolver test + runtime behavior) validated.
- [x] Destructive allowlist safety gates (`/` rejection, symlink rejection, canonical path checks).
- [x] Encrypted backup export/import with correct passphrase; wrong-passphrase failure handling.
- [x] Restore replace semantics verified (profile/history replaced atomically, no partial mutation).
- [x] Debug/release side-by-side install with:
  - [x] release `com.yshalsager.mafza`
  - [x] debug `com.yshalsager.mafza.debug`
- [x] Profile customization persistence validated via backup/restore roundtrip.
