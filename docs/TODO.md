# Mafza v1 Implementation TODO

## 0) Project Bootstrap
- [ ] Before each UI implementation/PR, consult `~/.codex/skill-sources/compose-skill/jetpack-compose-expert-skill/SKILL.md` and relevant `references/*`
- [x] Initialize Android project with modules `:app` and `:core`
- [x] Set `applicationId` release to `com.yshalsager.mafza`
- [x] Set debug `applicationId` to `com.yshalsager.mafza.debug` and `versionNameSuffix` `-debug`
- [x] Configure output artifacts: `mafza-<version>-debug.apk`, `mafza-<version>-release.apk`
- [x] Configure JDK 17 toolchain
- [x] Add stable-only dependency policy (no alpha/beta/rc)
- [x] Configure version catalog and pin latest stable versions

## 1) Core Domain Contracts
- [x] Add core enums: `ExecutionMode`, `StepStatus`, `RunStatus`, `ActionId`
- [x] Add core models: `DeleteTarget`, `ActionBinding`, `ActionPolicy`, `ShellCommandSpec`, `ProviderCapabilities`
- [x] Extend action model for instance-level control:
  - [x] `ActionId.SEND_SMS`
  - [x] `ActionBinding.binding_id`
  - [x] `ActionPolicy.policy_key`
- [x] Add `EmergencyProfile` with all profile customization fields
- [x] Add engine interfaces: `EmergencyEngine`, `EmergencyStep`
- [x] Add provider interface: `ActionProvider` (+ preflight and execute contracts)
- [x] Add backup contracts: `BackupPayload`, `BackupService`, `RestoreResult`
- [x] Dev-policy: no pre-v1 backward compatibility guarantees for profile schema/policy resolution

## 2) Persistence And Security
- [x] Implement DataStore-backed profile storage pipeline
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
- [x] Resolve policies by `policy_key` (instance-first) with action-level fallback

## 4) Actions (Live + Dry Run)
- [x] Location step with configurable timeout (`location_timeout_seconds`, default 8s)
- [x] SMS step (sequential fanout, per recipient logging)
- [x] Intent launch step (`IntentActionSpec`, live + dry-run behavior)
- [x] Message-app provider step (20s timeout, capability checks, unavailable fallback)
- [x] Allow multiple same-type action instances in one run:
  - [x] multiple message-app bindings
  - [x] multiple intent actions
- [x] Uninstall step via Shizuku (`pm uninstall --user 0 <package>`, 15s each)
- [x] Delete step via Shizuku (`rm`/`rmdir` contracts, 15s each target)
- [x] Advanced shell commands step:
  - [x] argv-safe mode (default)
  - [x] dangerous raw-shell mode (explicit opt-in)
  - [x] per-command timeout + continue-on-failure
- [x] Dry Run mode: no side effects, mark side-effect steps `SKIPPED_DRY_RUN`
- [x] Self uninstall executes strictly last

## 5) Validation And Preflight
- [x] Implement package/path validators (absolute path, canonical path, reject `/`, reject symlinks)
- [x] Implement action-binding validation (installed + launchable)
- [x] Implement action-policy validation (unique `execution_order` per branch)
- [x] Implement advanced-shell validation (`argv` required for safe mode, `raw_shell` gated)
- [x] Implement Live blocking checks matrix
- [x] Implement Dry Run partial preflight policy
- [x] Make SMS recipient requirement policy-driven (`required SEND_SMS` only)
- [x] Remove duplicate-binding hard block to allow same-type action instances
- [x] Implement warning states
  - [x] provider capability warnings
  - [x] backup passphrase not tested

## 6) Backup / Restore
- [x] Implement encrypted backup export to document URI
- [x] Implement crypto constants:
  - [x] PBKDF2-HMAC-SHA256 iterations `210000`
  - [x] salt `16 bytes`
  - [x] nonce `12 bytes`
  - [x] tag `128 bits`
  - [x] extension `.mafza.bak`
- [x] Include profile customization state in backup (`action_bindings`, `action_policies`, `advanced_shell_commands`, toggles)
- [x] Implement restore semantics as replace (not merge), atomically
- [x] Implement schema compatibility handling
- [x] Trigger post-restore preflight refresh

## 7) UI Foundation
- [x] Set up Navigation Compose routes: `Home`, `Profile`, `History`, `RunDetails(runId)`
  - [x] `Home`
  - [x] `Profile`
  - [x] `History`
  - [x] `RunDetails(runId)`
- [x] Implement app theme token layer (spacing/shape/typography abstractions)
  - [x] Material 3 theme baseline
- [x] Add dynamic color support:
  - [x] Android 12+: dynamic scheme
  - [x] Android 11: static Mafza palette fallback
- [x] Implement English + Arabic localization baseline and RTL enablement
- [x] Implement accessibility baseline (contrast, touch targets, semantics, dynamic type)

## 8) UI Screens
- [x] Home:
  - [x] Preflight status card + fix actions
  - [x] `Run Live` and `Run Dry Run` CTAs
  - [x] direct `Run Live` start (no confirmation dialog)
  - [x] full-screen cancel overlay (duration reflects user setting)
  - [x] health card (last successful Live/Dry Run + stale warning)
- [ ] Profile:
  - [x] Single settings form
  - [x] unified action list with top-bar add flow (add/remove/reorder all action types)
  - [x] grouped/collapsible sections with runtime order preview
  - [x] cancel-window duration setting (`cancel_window_seconds`)
  - [x] action timeout settings (`location_timeout_seconds`, `sms_timeout_seconds`, `intent_timeout_seconds`)
  - [x] action/provider pickers (message app + uninstall package + delete file/dir + contact picker)
  - [x] provider/intent test actions wired in UI
  - [x] action policy controls (enabled/required/continue/order, per-instance via `policy_key`)
  - [x] advanced shell command editor (add/edit/reorder)
  - [x] global safety toggles (`Destructive Actions Enabled`, `Triggers Enabled`)
  - [x] biometric/PIN gate for sensitive edits
  - [x] backup/restore UI flow (export, restore, preview, confirm)
- [x] History:
  - [x] compact rows + expandable step details
  - [x] mode/status badges
  - [x] run details navigation

## 9) Triggers And Surface Integrations
- [x] Launcher shortcut trigger
- [x] Home widget trigger and labels
- [x] QS tile trigger and labels
- [x] Ensure external triggers always force `LIVE`
- [x] Honor profile `triggers_enabled` toggle for external trigger sources

## 10) Test Coverage
- [x] Core unit tests for engine status/policy resolution, validators, and step behavior
- [x] Unit tests for backup payload/schema/crypto failure handling
- [x] Unit tests for advanced shell policy and execution gating
- [x] Device tests for core action-step execution on AVD:
  - [x] intent launch step live-path
  - [x] message-provider step with capability-gated share target
  - [x] SMS step permission-gate behavior
- [x] Instrumentation tests for trigger routing, service lifecycle, UI flows
  - [x] trigger routing and external-trigger mode/toggle behavior
  - [x] foreground service lifecycle
  - [x] key UI flows (profile top-bar add -> action creation -> dirty-state)
- [x] Device tests for Shizuku authorized/unauthorized behavior (authorized test runs when Shizuku is granted; otherwise skipped)
- [x] Device tests for provider unavailable fallback and dry-run no-side-effects
- [x] Screenshot regression tests for key UI states
- [x] Verify debug/release side-by-side install

## 11) CI And Release Gates
- [x] Configure CI: lint + static analysis + unit tests + instrumentation/screenshot jobs
  - [x] baseline workflow (`.github/workflows/ci.yml`) for lint + unit + assemble + emulator instrumentation
  - [x] add screenshot regression workflow (`.github/workflows/screenshots.yml`) with fastlane screengrab
- [x] Enforce dependency/security checks (locking + secret scanning)
  - [x] add Renovate updates (`renovate.json5`) for Gradle, Gradle Wrapper, and GitHub Actions
  - [x] add secret scanning workflow (`.github/workflows/security.yml`) via gitleaks
  - [x] add dependency locking strategy for Gradle artifacts (`gradle.lockfile`)
- [x] Enforce must-pass release checklist from `PLAN.md`
  - [x] add manual release gate workflow (`.github/workflows/release-gate.yml`)
  - [x] add checklist verifier (`scripts/verify_release_checklist.sh`) for `docs/RELEASE_CHECKLIST.md`
- [x] Produce signed internal release APK and debug APK artifacts
  - [x] CI uploads deterministic names: `mafza-debug.apk`, `mafza-release.apk`
- [x] Add release automation (`.github/workflows/release.yml`) with tagged builds and GitHub release artifacts
