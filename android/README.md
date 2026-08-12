# Samsung Hz Ops — native Android implementation

This module is the production Android path for the Material 3 prototype in the repository root. The application id is `com.xtawa.samsunghzops`; it intentionally does not reuse the reference APK id (`com.tribalfs.gmh`).

## Architecture

- `RefreshRateRepository` reads `DisplayManager`/`Display.Mode` and is the only refresh-rate writer exposed to UI, services, QS Tile and Tasker integration.
- `RefreshPolicyEngine` merges foreground-app, lock-screen, display, camera, cast and fold signals into one `RefreshPolicyDecision`.
- `SettingsBackend` is the namespace-aware bridge. `Settings.System` uses the user-granted `WRITE_SETTINGS` capability; Secure/Global keys require the Shizuku bridge or a future trusted companion.
- `TransactionCoordinator` reads every old value, applies a multi-key change, verifies the backend result and rolls back already-applied keys on failure. The latest 50 records are available for diagnostics.
- `ProfileDatabase` stores per-app policies in Room; DataStore stores user preferences and feature toggles.
- `AutomationService`, `HzAccessibilityService`, `RefreshMonitorService`, Boot receiver and QS Tile are thin adapters. They do not write Settings directly.

## Samsung-specific behavior

The PSM adapter listens for `PowerManager.ACTION_POWER_SAVE_MODE_CHANGED` and isolates Samsung Global keys in `SamsungPsmMapping`. The default mapping is labelled as reverse-engineering inference and must be read back on each target One UI/firmware before shipping a calibrated profile. Thermal hooks are not used.

The documented keys are centralized in `data/settings/SettingsFieldRegistry.kt`. In particular, `user_refresh_rate` is treated as a `Settings.System` fallback; unverified cover variants are not silently assumed.

## Building

The repository includes `.github/workflows/android.yml`, which installs Java 17, Android API 35 and Gradle 8.10.2, then runs unit tests, `assembleDebug` and `lintDebug`. The current development container does not include an Android SDK, so local APK compilation must be performed on that workflow or a machine with the Android SDK installed.

## Permission and safety boundaries

The app remains readable without elevated access. It never claims that `WRITE_SECURE_SETTINGS` was granted merely because the permission appears in the manifest. Every privileged operation reports the missing capability and leaves the system unchanged when the bridge is unavailable. OEM values that are not confirmed by the device are presented as unsupported/calibration-required rather than guessed.
