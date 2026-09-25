# ComboShip Android

This directory is a source integration overlay for Varuuna/ComboShip at 94eb185e4abcc2d568aa8241fa02c43cdd86c439. It keeps the real two-game runtime and combined randomizer rather than wrapping two standalone apps.

**Status: the real ARM64 runtime builds, installs, and renders its native ROM setup screen in an Android 15 emulator. Gameplay on a physical device has not yet been tested.** Android 9+, ARM64, and OpenGL ES 3.0 are required. Compilation and startup checks do not establish complete QoL parity or a playable seed.

The app identity is `org.comboship.android`, separate from `com.twoshipfork.mm`. Existing 2S2H data is not reset, overwritten, or automatically migrated. ROMs are supplied by the user; no game ROMs or ROM-derived game archives are included in the repository or build artifacts.

## Use the app

1. Install the ARM64 development APK. Open ComboShip Android and let it verify its support files.
2. Import your own supported OoT and MM ROMs. Import accepts `.z64`, `.v64` and `.n64` byte ordering; the native extractor checks the game/version.
3. Choose **Play / open combined randomizer**, complete extraction, and use ComboShip's native combined seed generator. Web OoTMM seed compatibility is not implied.
4. Tap **Menu**, Android Back, or controller Select to open native settings. Hold **Menu** for touch layout, menu size, and exit controls. Save in-game before exiting.
5. Use setup to import mod archives, select app storage or SD storage, and export saves/settings/logs. **Play without mods** skips both games' mods for that launch without changing mod files or their stored preferences.

## Included Android work

| Feature | Implementation and limits |
| --- | --- |
| Combined runtime and randomizer | Both actual game libraries, shared engine, ComboShip UI, generator, transitions and cross-game hooks retained from the pinned ComboShip source. Device validation pending. |
| Existing game enhancements | Native graphics, audio, controls, camera, timesaver, tracker and randomizer menus retained from ComboShip's OoT/MM trees. They are not all independently tested on Android. |
| Touch controls | Multi-touch virtual SDL gamepad, movement/camera sticks, C buttons, D-pad, shoulders, Start, Nintendo/Xbox/GameCube face layouts, visibility, opacity, size and drag layout. Layouts are stored by screen shape and preset. |
| Menu usability | Adjustable native menu scale, touch controls suppressed over native menus, Android Back and controller Select support. |
| Storage | App-owned internal/shared/SD locations; hash-verified copy before switching; original copy preserved. Missing SD storage is reported instead of silently creating a new save. |
| Fork fixes | MM cutscene/interpolation/clock/pictograph backports, user's Grandma background texture fix, enlarged MM save-editor touch targets and separated ammo fields. |
| Recovery | Launch without mods; native startup log and previous log; Java exception report; Android process-exit reports on API 30+ and traces where the OS provides them. |
| Imports/backups | Atomic byte-order-normalizing ROM import, separate mod folders, save/settings/log export, and cross-process locking to prevent imports or backups during gameplay. Backup restoration is manual. |
| Packaging | Eight real ARM64 libraries, GLES shaders, both support archives, extractor XML, controller database, dependency notices, and 16 KiB ELF alignment checks. |

Reference sources: [ComboShip](https://github.com/Varuuna/ComboShip/tree/94eb185e4abcc2d568aa8241fa02c43cdd86c439), [user's 2S2H Android fork](https://github.com/swunderly/2ship2harkinian-Android/tree/11d8fb3c067c397ac038d6372fde0cdb436b097b), [Grandma fix](https://github.com/swunderly/2ship2harkinian-Android/commit/c3e2886c638af11b7514e52455b1f3d8b9789aed), [save-editor touch fix](https://github.com/swunderly/2ship2harkinian-Android/commit/9031e2d7b85d999782e8083a391ad52572e1d511), and [Shipwright Android](https://github.com/linkzenic/Shipwright-Android/tree/94e8d7af3fb3a2e21fb5bf1d250cd0a63a5dd09d).

Build instructions are in [BUILDING.md](BUILDING.md), completed checks in [VALIDATION.md](VALIDATION.md), and outstanding phone/gameplay checks in [DEVICE_TESTS.md](DEVICE_TESTS.md).

Use `python3 ComboAndroid/tools/apply_port.py /path/to/fresh/pinned/ComboShip` to stage the overlay and apply checked Android-specific patches. It refuses a wrong revision, tracked edits, or an already-patched tree.

Source licenses remain those of each upstream component. Added port-specific code is available under MIT; this does not relicense the upstream games, engine, or dependencies.
