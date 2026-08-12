# Samsung Hz Ops — Material 3 UI prototype

An interactive mobile UI prototype for a Samsung refresh-rate controller. This branch intentionally contains no Android system integration.

## Included in this prototype

- **Control:** live Hz readout, Standard / Adaptive / Maximum modes, minimum and maximum refresh-rate ranges, and high refresh while power saving.
- **Rules:** per-app refresh rate, Adaptive Mod, mode-restore guard, Normal / PSM / low-battery / AOD profiles, Fold and Flip dual-screen profiles, and screen-off auto PSM.
- **Tools:** resolution, AOD refresh rate, refresh monitor, animation scales, Quick Doze, battery protection, auto-sync, sensors-off automation, resizable windows, network-speed indicator, and developer tiles.
- **More:** master switch, Shizuku and permission readiness, device capability detection, Root / LSPosed compatibility, Quick Settings tiles, Tasker / Locale, app shortcuts, appearance and language, updates, and about.
- Material 3 list, detail, search, switch, slider, segmented-control, and bottom-navigation states.
- Pixel 10 and iPhone preview shells.

All controls use local React state. They do **not** access Android Settings, Shizuku, ADB, root, Accessibility services, or display APIs. Values and compatibility states are illustrative.

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
