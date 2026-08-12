# Samsung Hz Ops — Material 3 prototype + native Android implementation

The repository contains the web UI reference prototype and a native Kotlin/Jetpack Compose Material 3 module under [`android/`](android/). The native module implements the refresh-rate policy, settings capability boundary, transaction rollback, Samsung PSM adapter, per-app Room profiles, foreground/accessibility services, QS Tile, shortcuts and the documented tools.

## Included in this prototype

- **Control:** live Hz readout, Standard / Adaptive / Maximum modes, minimum and maximum refresh-rate ranges, and high refresh while power saving.
- **Rules:** per-app refresh rate, Adaptive Mod, mode-restore guard, Normal / PSM / low-battery / AOD profiles, Fold and Flip dual-screen profiles, and screen-off auto PSM.
- **Tools:** resolution, AOD refresh rate, refresh monitor, animation scales, Quick Doze, battery protection, auto-sync, sensors-off automation, resizable windows, network-speed indicator, and developer tiles.
- **More:** master switch, Shizuku and permission readiness, device capability detection, Root / LSPosed compatibility, Quick Settings tiles, Tasker / Locale, app shortcuts, appearance and language, updates, and about.
- Material 3 list, detail, search, switch, slider, segmented-control, and bottom-navigation states.
- Pixel 10 and iPhone preview shells.

The web prototype remains a visual preview and does not access Android APIs. Native Android behavior and its permission boundaries are documented in [`android/README.md`](android/README.md). Samsung OEM values that require firmware calibration are explicitly labelled as inference/unsupported until read-back is verified on the target device.

## Run locally

```bash
npm ci
npm run dev
```

## Validate

```bash
npm run check:runtime
npm run build
```

Native build/test/lint run in GitHub Actions via [`.github/workflows/android.yml`](.github/workflows/android.yml); this development container does not include an Android SDK.
