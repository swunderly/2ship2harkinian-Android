# Development build validation — 2026-09-25

APK: `ComboShip-Android-0.1.0-dev-arm64.apk`, 49,768,095 bytes.

SHA-256: `1c4c14f41f48d9734dd4a234c491678c750a3b02909a8c76c458254680cc78ac`

This is an experimental development APK, signed with a local Android debug key. It is not a stable release or a claim of full feature parity.

## Completed

- Built both real game engines, the combined launcher/UI, shared engine, SDL2, SDL2_net and C++ runtime for ARM64/API 28 with NDK 28.2.13676358.
- Generated both support archives with native host ZAPD, without using a ROM.
- Assembled the APK and passed Android lint with zero errors. Remaining warnings include upstream SDL compatibility code and localization/deprecation notices. Lint exclusions are limited to SDL's optional permission-gated Bluetooth/microphone code; no Bluetooth or microphone permission is requested.
- Verified eight ARM64 ELF libraries, their packaged dependencies, all 154 launcher-requested game callbacks, JNI entry points, 16 KiB ELF load alignment, 9,036 support-file hashes, APK v2 signature, and absence of ROM/game-archive filenames.
- Passed 37 Java ROM-import and record-lock assertions, five filesystem storage-copy assertions on Windows, and the C++ concurrent input snapshot/release test.
- Applied the final overlay to a clean pinned upstream checkout and compared all 32 modified upstream files with the compiled source; no differences after newline normalization. The user-fix backport script was also checked for repeat application.
- Installed the ARM64 APK on an Android 15/API 35 Google APIs x86_64 emulator using ARM translation and a software GLES 3.0 renderer. Confirmed the installed APK hash matches the delivered APK.
- Opened Android setup, installed its support files, loaded both native game libraries, and rendered the combined native ROM setup UI in landscape.
- Used empty files only in the disposable emulator to exercise missing/invalid ROM handling. They were not accepted as games. Opened touch options and the layout editor; changed native menu scale to 200% and verified `AndroidScale: 2.0` persisted in the game configuration.

## Limits and known test-environment issue

No actual ROM extraction, seed playthrough, cross-game transition, audio during gameplay, game-save reload, physical controller or SD-card hardware test has run. These require the user's ROMs and an Android device. The source contains the combined randomizer and ported QoL features, but their full gameplay parity remains unverified.

An older Android 11/API 30 emulator installed the app and loaded the engines, but its ARM translator stopped on instruction `0x5ea1b821` before rendering. The Android 15 emulator rendered successfully. Neither result establishes compatibility on a physical Android 11 device.

The development signing identity is local to this build. Keep backups before changing builds or signing keys. APKs made by another machine/CI may require a separate installation if signed differently.
