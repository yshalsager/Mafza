# Mafza v1 Implementation TODO

## 0) Project Bootstrap
- [x] Initialize Android project with modules `:app` and `:core`
- [x] Set `applicationId` release to `com.yshalsager.mafza`
- [x] Set debug `applicationId` to `com.yshalsager.mafza.debug` and `versionNameSuffix` `-debug`
- [x] Configure output artifacts: `mafza-debug.apk`, `mafza-release.apk`
- [x] Configure JDK 17 toolchain
- [x] Add stable-only dependency policy (no alpha/beta/rc)
- [x] Configure version catalog and pin latest stable versions

## 1) Core Domain Contracts
- [x] Add core enums: `ExecutionMode`, `StepStatus`, `RunStatus`, `ActionId`
- [x] Add core models: `DeleteTarget`, `ActionBinding`, `ActionPolicy`, `ShellCommandSpec`, `ProviderCapabilities`
- [x] Add `EmergencyProfile` with all profile customization fields
- [x] Add engine interfaces: `EmergencyEngine`, `EmergencyStep`
- [x] Add provider interface: `ActionProvider` (+ preflight and execute contracts)
- [x] Add backup contracts: `BackupPayload`, `BackupService`, `RestoreResult`

## 2) Persistence And Security
- [ ] Implement DataStore (Proto) for profile storage
- [x] Implement encrypted DataStore profile storage (typed serializer baseline)
- [x] Implement Room for run/step history
- [x] Add retention pruning to keep latest 100 runs
- [x] Implement encrypted-at-rest profile handling (Keystore-backed)
- [x] Implement redacted command audit model in history
- [x] Implement audit export as redacted JSON

## 3) Execution Engine
- [x] Implement `EmergencyExecutionService` with single active run (`Mutex`)
- [x] Implement `EmergencyStartReceiver` and trigger routing contract
- [x] Implement duplicate trigger ignore behavior (`IGNORED_DUPLICATE_TRIGGER`)
- [x] Implement user-configurable cancel window (default 2s) + `cancelWithinWindow`
- [x] Implement run snapshot behavior (profile/bindings/policies/commands frozen at start)
- [x] Implement run state and completion rules (`SUCCESS/PARTIAL/FAILED`)
- [x] Implement branch orchestration and action `execution_order`
- [x] Implement per-action `required` and `continue_on_failure` logic

## 4) Actions (Live + Dry Run)
- [x] Location step with configurable timeout (`location_timeout_seconds`, default 8s)
- [x] SMS step (sequential fanout, per recipient logging)
- [x] Intent launch step (`IntentActionSpec`, live + dry-run behavior)
- [x] Message-app provider step (20s timeout, capability checks, unavailable fallback)
- [x] Uninstall step via Shizuku (`pm uninstall --user 0 <package>`, 15s each)
- [x] Delete step via Shizuku (`rm`/`rmdir` contracts, 15s each target)
- [x] Advanced shell commands step:
  - [x] argv-safe mode (default)
  - [x] dangerous raw-shell mode (explicit opt-in)
  - [x] per-command timeout + continue-on-failure
- [x] Dry Run mode: no side effects, mark side-effect steps `SKIPPED_DRY_RUN`
- [ ] Self uninstall executes strictly last

## 5) Validation And Preflight
- [ ] Implement package/path validators (absolute path, canonical path, reject `/`, reject symlinks)
- [ ] Implement action-binding validation (installed + launchable)
- [ ] Implement action-policy validation (unique `execution_order` per branch)
- [ ] Implement advanced-shell validation (`argv` required for safe mode, `raw_shell` gated)
- [ ] Implement Live blocking checks matrix
- [ ] Implement Dry Run partial preflight policy
- [ ] Implement warning states (provider capability warnings, backup passphrase not tested)

## 6) Backup / Restore
- [ ] Implement encrypted backup export to document URI
- [ ] Implement crypto constants:
  - [ ] PBKDF2-HMAC-SHA256 iterations `210000`
  - [ ] salt `16 bytes`
  - [ ] nonce `12 bytes`
  - [ ] tag `128 bits`
  - [ ] extension `.mafza.bak`
- [ ] Include profile customization state in backup (`action_bindings`, `action_policies`, `advanced_shell_commands`, toggles)
- [ ] Implement restore semantics as replace (not merge), atomically
- [ ] Implement schema compatibility handling
- [ ] Trigger post-restore preflight refresh

## 7) UI Foundation
- [ ] Before each UI implementation/PR, consult `~/.codex/skill-sources/compose-skill/jetpack-compose-expert-skill/SKILL.md` and relevant `references/*`
- [ ] Set up Navigation Compose routes: `Home`, `Profile`, `History`, `RunDetails(runId)`
- [ ] Implement app theme with Material 3 + token layer
- [ ] Add dynamic color support:
  - [ ] Android 12+: dynamic scheme
  - [ ] Android 11: static Mafza palette fallback
- [ ] Implement English + Arabic localization and RTL layout support
- [ ] Implement accessibility baseline (contrast, touch targets, semantics, dynamic type)

## 8) UI Screens
- [ ] Home:
  - [x] Preflight status card + fix actions
  - [ ] `Run Live` and `Run Dry Run` CTAs
  - [ ] Live confirmation dialog
  - [ ] full-screen cancel overlay (duration reflects user setting)
  - [ ] health card (last successful Live/Dry Run + stale warning)
- [ ] Profile:
  - [ ] Single settings form
  - [ ] cancel-window duration setting (`cancel_window_seconds`)
  - [ ] action timeout settings (`location_timeout_seconds`, `sms_timeout_seconds`, `intent_timeout_seconds`)
  - [ ] action/provider picker and provider test actions
  - [ ] action policy controls (enabled/required/continue/order)
  - [ ] advanced shell command editor (add/edit/reorder)
  - [ ] global safety toggles (`Destructive Actions Enabled`, `Triggers Enabled`)
  - [ ] biometric/PIN gate for sensitive edits
  - [ ] backup/restore UI flow (export, restore, preview, confirm)
- [ ] History:
  - [ ] compact rows + expandable step details
  - [ ] mode/status badges
  - [ ] run details navigation

## 9) Triggers And Surface Integrations
- [ ] Launcher shortcut trigger
- [ ] Home widget trigger and labels
- [ ] QS tile trigger and labels
- [ ] Ensure external triggers always force `LIVE`

## 10) Test Coverage
- [ ] Unit tests for state machine, policies, validators, dry-run guarantees
- [ ] Unit tests for backup payload/schema/crypto failure handling
- [ ] Unit tests for advanced shell policy and execution gating
- [ ] Instrumentation tests for trigger routing, service lifecycle, UI flows
- [ ] Device tests for Shizuku authorized/unauthorized behavior
- [ ] Device tests for provider unavailable fallback and dry-run no-side-effects
- [ ] Screenshot regression tests for key UI states
- [ ] Verify debug/release side-by-side install

## 11) CI And Release Gates
- [ ] Configure CI: lint + static analysis + unit tests + instrumentation/screenshot jobs
- [ ] Enforce dependency/security checks (locking + secret scanning)
- [ ] Enforce must-pass release checklist from `PLAN.md`
- [ ] Produce signed internal release APK and debug APK artifacts
