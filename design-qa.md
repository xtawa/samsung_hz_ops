# Design QA — Samsung Hz Ops Material 3 prototype

## Evidence

- Core-control visual source: `/workspace/scratch/41a92f746afd/generated_images/exec-3a501958-f8d1-4df9-a3b8-0bcd4d652da5.png`
- Settings-list visual source: `/workspace/scratch/41a92f746afd/generated_images/exec-5c32fe99-86af-4020-981f-1d24240d5968.png`
- Implementation checked in the cloud browser at `http://terminal.local:4173/` using the protected Pixel 10 runtime.
- Browser viewport: `1363 × 936` CSS px. Pixel app screen: nominal `427 × 952`, scaled to the preview stage.
- State: light theme, UI-only local state, no Android system access.

## Visual comparison

The core page preserves the approved hierarchy: compact app bar, dominant current-Hz readout, segmented mode control, independent minimum and maximum sliders, power-saving high-refresh switch, and permission status. The expanded surfaces follow the approved Google Settings direction: blue section labels, pale tonal containers, circular leading icons, restrained dividers, right-aligned state values, Material switches, and a four-destination bottom navigation bar.

Typography uses local Roboto 400/500. Layout uses Material-sized 48 px touch targets, 16–24 px content gutters, low-elevation tonal grouping, and a single blue accent. No gradients, decorative imagery, dashboard gauges, or feature tiles were introduced.

## Feature coverage

- Control: mode, min/max Hz, PSM high refresh, permission state.
- Rules: per-app, adaptive behavior, restore guard, Normal / PSM / low-battery / AOD / Fold profiles, auto PSM.
- Tools: resolution, AOD Hz, monitor, animation, Quick Doze, battery protection, sync, sensors, resizable windows, network speed, developer tiles.
- More: master state, permissions and Shizuku, device capability, Root / LSPosed, Quick Settings tiles, Tasker, shortcuts, appearance, updates, about.
- Every listed feature opens a local detail or toggles a local preview state.

## Findings and fixes

- [Resolved P1] The first implementation exposed only the core screen. Added Rules, Tools, More, and detail surfaces while keeping the home screen focused.
- [Resolved P2] Navigation initially retained the prior page's scroll position. Keyed the scroll container by destination and detail route so each page opens at the top.
- [Resolved P2] Toggle rows initially nested a switch button inside a row button. Converted rows to keyboard-accessible `role="button"` containers and preserved independent switch interaction.
- [P3] The generated mock uses a marginally softer primary-container tint. The implementation keeps the practical Material token `#d9e2ff` for contrast consistency.

No open P0, P1, or P2 visual or interaction issue remains.

## Interaction checks

- Four bottom-navigation destinations open and reset scroll position.
- Rule rows open detail pages; back returns to the correct destination.
- Mode, sliders, switches, search preview, cancel, and overflow reset respond in local state.
- Quick Doze switch changed from unchecked to checked without opening the row.
- All feature sections remain reachable through mobile scrolling above the fixed navigation bar.
- No application-origin console errors were observed. Logged errors came only from the cloud-browser extension origin.

## Build checks

- [x] `npm run check:runtime`
- [x] `npm run build`
- [x] Pixel 10 visual inspection
- [x] Primary navigation and detail flows
- [x] P0/P1/P2 issues resolved

final result: passed
