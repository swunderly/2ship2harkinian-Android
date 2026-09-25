# Build ComboShip Android

The overlay pins Varuuna/ComboShip to `94eb185e4abcc2d568aa8241fa02c43cdd86c439` and vcpkg to `10541e317a660f4165ba4ac2851ab54a8d4577b1`. Use a **fresh checkout** of that ComboShip revision. The patcher deliberately refuses existing modifications and repeat application. It never resets a checkout.

Prerequisites: Git, Python 3.10+, Java 17 or 21, Android SDK platform 35, build-tools 35.0.0, NDK 28.2.13676358, CMake 3.31.5, Ninja, and a bootstrapped vcpkg at the pinned revision. Windows also needs Visual Studio's C++ desktop workload; Linux needs the packages listed in the CI workflow and ccache. Allow several GB of disk space and substantial memory for the two game engines.

```sh
git clone https://github.com/Varuuna/ComboShip.git upstream
git -C upstream checkout 94eb185e4abcc2d568aa8241fa02c43cdd86c439
git clone https://github.com/microsoft/vcpkg.git vcpkg
git -C vcpkg checkout 10541e317a660f4165ba4ac2851ab54a8d4577b1
```

On Windows, bootstrap vcpkg and then run from a Visual Studio **x64 developer PowerShell**:

```powershell
./vcpkg/bootstrap-vcpkg.bat -disableMetrics
./ComboAndroid/tools/build.ps1 -Upstream ./upstream -Vcpkg ./vcpkg `
    -Workspace ./combo-build -AndroidSdk C:/Android/sdk -JavaHome C:/Java/jdk-21
```

On Linux/WSL, export `ANDROID_HOME`, `ANDROID_NDK_HOME`, and `JAVA_HOME`, bootstrap vcpkg, create the output directory, then run:

```sh
./vcpkg/bootstrap-vcpkg.sh -disableMetrics
mkdir -p combo-build
bash ComboAndroid/tools/build.sh ./upstream ./vcpkg ./combo-build
```

The pipeline compiles eight ARM64 shared libraries, uses a native host ZAPD to generate `soh.o2r` and `2ship.o2r` from repository support assets, merges the extractor XML trees, validates ELF dependencies/entry points and 16 KiB page alignment, then builds and signs a debug APK with the pinned Gradle wrapper. No ROM is consumed during the build. The APK and checksum are written to `apk-output` inside the workspace.

For an interrupted build, continue the failed CMake/Gradle command in its existing build directory. Do not rerun the patcher on the modified checkout. Regenerate staged files with `stage_runtime.py` after changes to native code. The staging tool replaces only its generated app assets, SDL Java, and JNI library directories.

Tests on Linux: `bash ComboAndroid/tools/run_tests.sh`. The CMake input test also runs with MSVC on Windows; POSIX path/session-lock tests require Linux. The Java ROM, record-lock and verified-storage-copy tests are platform independent. The full Android Java build validates against API 35.

Development APKs use the builder's Android debug signing key. An APK from a different builder or CI may have a different signature. Keep the signing key locally for updates, and export saves before changing installation/signing identity. Private keys are not in the source archive.

Source replay is checked against a fresh pinned checkout. Device tests, including actual extraction and cross-game saves, are tracked in [DEVICE_TESTS.md](DEVICE_TESTS.md).
