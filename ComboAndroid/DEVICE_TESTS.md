# Device validation still required

The ARM64 native build, APK checks and Android 15 emulator startup checks run on the build computer. They do not establish gameplay correctness. No physical Android device or game ROM was available. See [VALIDATION.md](VALIDATION.md) for completed checks.

Record phone/tablet model, Android version, GPU, controller, app version and APK checksum. Use a separate test save and export a backup before updating builds.

1. Install on Android 9 or later with ARM64 and OpenGL ES 3.0. Confirm setup opens and bundled support verification succeeds.
2. Import supported OoT and MM ROMs through the document picker; test `.z64`, `.v64` and `.n64` byte ordering. Confirm interrupted imports preserve the previous file.
3. Launch, complete native ROM validation/extraction, and confirm both game archives appear. Relaunch without repeating extraction.
4. Generate a combined seed using ComboShip's own generator. Verify the spoiler/settings files and start a new save.
5. Exercise OoT → MM → OoT transitions, cross-game item delivery, linked inventory, save/reload, and cycle reset. Play through a representative combined seed before calling the build stable.
6. Test graphics/audio in both games, higher frame rates, free camera, widescreen, frame interpolation, background/resume and screen rotation. Check Anju's grandmother's background and the enlarged MM save editor.
7. Test movement plus multiple simultaneous buttons, all C buttons, camera, D-pad, menu tap/hold, Android Back, controller Select, keyboard input and controller reconnection. Confirm native menus receive touches with the overlay hidden.
8. Test Nintendo/Xbox/GameCube layouts, move/resize/opacity/floating-stick settings, menu scaling, screen aspect changes and preference persistence.
9. Import one compatible mod per game. Test a launch without mods, then a normal launch; mod files and enable preferences should remain intact.
10. Copy data to SD storage, verify saves after relaunch, return to the preserved original location, and test an unavailable SD card. Confirm an active game prevents imports, storage copying and backup export.
11. Export saves/settings/logs; inspect the ZIP and verify it omits ROMs, game archives, mods and bundled support data. Backups currently require manual file restoration; there is no in-app restore importer.
12. Check native/Java logs and Android exit reports after a failed launch. Android exit history requires API 30+, and native trace availability depends on Android/OEM support.

Report failures with the exported diagnostic ZIP. ROMs are not needed in a bug report. Web OoTMM-generated seeds are a separate format and are not certified compatible with this runtime.
