# Mafza Internal Release Checklist

Source of truth: `docs/PLAN.md` section `Release Acceptance Checklist (Must Pass Before Internal Release)`.

- [ ] Live run from all triggers with full preflight green.
- [ ] Dry Run from in-app action with zero external side effects.
- [ ] Message-app provider selection, test-provider flow, unavailable-provider fallback.
- [ ] Intent action step configuration and execution (resolver test + runtime behavior) validated.
- [ ] Destructive allowlist safety gates (`/` rejection, symlink rejection, canonical path checks).
- [ ] Encrypted backup export/import with correct passphrase; wrong-passphrase failure handling.
- [ ] Restore replace semantics verified (profile/history replaced atomically, no partial mutation).
- [ ] Debug/release side-by-side install with:
  - [ ] release `com.yshalsager.mafza`
  - [ ] debug `com.yshalsager.mafza.debug`
- [ ] Profile customization persistence validated via backup/restore roundtrip.
