# Mafza

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-30-2ea44f)](https://developer.android.com/about/versions/android-11)
[![Shizuku](https://img.shields.io/badge/destructive%20actions-Shizuku%20optional-4f46e5)](https://github.com/RikkaApps/Shizuku)

Emergency actions runner with one configurable profile, external emergency triggers, and a safe Dry Run mode.

> [!CAUTION]
> This project was developed with heavy use of AI assistance, including OpenAI Codex.

## Screenshots

<table>
  <thead>
    <tr>
      <th align="left">English</th>
      <th align="left">Arabic (RTL)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>
        <div><strong>Home</strong> (preflight + run controls)</div>
        <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01_home.png" alt="Home (EN)" style="max-width: 100%; height: auto;" />
      </td>
      <td>
        <div><strong>Home</strong> (الصفحة الرئيسية)</div>
        <img src="fastlane/metadata/android/ar/images/phoneScreenshots/01_home.png" alt="Home (AR)" style="max-width: 100%; height: auto;" />
      </td>
    </tr>
    <tr>
      <td>
        <div><strong>Profile</strong> (actions + policies)</div>
        <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02_profile.png" alt="Profile (EN)" style="max-width: 100%; height: auto;" />
      </td>
      <td>
        <div><strong>Profile</strong> (الإعدادات)</div>
        <img src="fastlane/metadata/android/ar/images/phoneScreenshots/02_profile.png" alt="Profile (AR)" style="max-width: 100%; height: auto;" />
      </td>
    </tr>
    <tr>
      <td>
        <div><strong>Action Drawer</strong> (available actions)</div>
        <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03_actions_drawer.png" alt="Actions drawer (EN)" style="max-width: 100%; height: auto;" />
      </td>
      <td>
        <div><strong>Action Drawer</strong> (لوحة الإجراءات)</div>
        <img src="fastlane/metadata/android/ar/images/phoneScreenshots/03_actions_drawer.png" alt="Actions drawer (AR)" style="max-width: 100%; height: auto;" />
      </td>
    </tr>
    <tr>
      <td>
        <div><strong>History</strong> (runs + statuses)</div>
        <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04_history.png" alt="History (EN)" style="max-width: 100%; height: auto;" />
      </td>
      <td>
        <div><strong>History</strong> (السجل)</div>
        <img src="fastlane/metadata/android/ar/images/phoneScreenshots/04_history.png" alt="History (AR)" style="max-width: 100%; height: auto;" />
      </td>
    </tr>
  </tbody>
</table>

## What This App Does

- Runs emergency pipelines in `Live` or `Dry Run`.
- Supports external trigger surfaces: launcher shortcut, home widget, and Quick Settings tile.
- Uses a pre-start cancel window and preflight checks before live execution.
- Sends communication actions via SMS, message-app provider bindings, and Telegram bot actions.
- Supports custom intent actions as executable steps.
- Supports destructive actions with explicit allowlists:
  - uninstall package allowlist
  - path/content URI delete allowlist
  - advanced shell commands
- Gates destructive live actions on Shizuku availability/permission.
- Captures run history with step-level statuses and redacted command audit.
- Supports encrypted backup/restore for profile and optional history (replace semantics).
- Captures cell metadata and supports optional OpenCellID fallback when platform location is unavailable.

## Requirements

- Android `minSdk 30` (Android 11+)
- Runtime permissions for configured live features (`ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `READ_PHONE_STATE`, `SEND_SMS`)
- Shizuku installed/running and permission granted if destructive live actions are enabled
- Optional OpenCellID API key for location fallback

## Architecture

Core pieces:

- `:app` and `:core` modules
- `MainActivity`: Compose host for `Home`, `Profile`, `History`
- `EmergencyExecutionService`: foreground execution runtime and run orchestration
- `EmergencyStartReceiver`: shared external trigger receiver
- `EmergencyShortcutProxyActivity`, `EmergencyWidgetProvider`, `EmergencyQuickSettingsTileService`: trigger surfaces
- `PreflightValidator`: live/dry-run readiness checks
- `EncryptedProfileStore` + DataStore: encrypted profile persistence
- `RunHistoryStore` + Room: run history and command audit retention
- `EncryptedBackupService`: encrypted export/import with rollback on restore failure
- `EmergencyStepsFactory` + step implementations: location, notify, intent, and destructive execution

## Build

This project uses [mise](https://mise.jdx.dev/) for tool management.

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Build release APK
./gradlew :app:assembleRelease

# Run app + core unit tests
./gradlew :core:testDebugUnitTest :app:testDebugUnitTest

# Build instrumentation test APK
./gradlew :app:assembleDebugAndroidTest
```

## Fastlane And Metadata

This repo includes:

- `fastlane/metadata/android/en-US`
- `fastlane/metadata/android/ar`
- screenshot assets under `fastlane/metadata/android/*/images/phoneScreenshots`
- lanes for metadata validation, screenshot capture, and Play upload

Useful commands:

```bash
# Install fastlane gems
bundle install

# Validate metadata
bundle exec fastlane android validate_metadata

# Capture screenshots
bundle exec fastlane android capture_screenshots
```

## CI

GitHub Actions workflows:

- `ci.yml`: lint, unit tests, assemble, instrumentation subset, metadata validation
- `screenshots.yml`: emulator-based screenshot capture and optional PR
- `release.yml`: version/tag flow, screenshot refresh dependency, release artifacts, optional GitHub release

Dependency updates are managed by `renovate.json5`.

## Internal Docs

- Product/technical plan: `docs/PLAN.md`
- Release checklist: `docs/RELEASE_CHECKLIST.md`
- Remaining tasks: `docs/TODO.md`
