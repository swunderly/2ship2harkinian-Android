#define SDL_MAIN_HANDLED
#include <SDL.h>
#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <stdexcept>
#include <string>
#include <unistd.h>
#include "InputState.h"
#include "RuntimePaths.h"
#include "ship/port/mobile/MobileImpl.h"
int ComboAndroid_RunDesktopMain(int argc, char** argv);
namespace {
comboandroid::InputState input;
std::atomic<bool> quitRequested{false}, gameplayActive{false};
SDL_Joystick* touchJoystick = nullptr;
void updateTouch(void*) {
    if (!touchJoystick) return;
    const auto state = input.snapshot();
    for (int i = 0; i < SDL_CONTROLLER_BUTTON_MAX; ++i)
        SDL_JoystickSetVirtualButton(touchJoystick, i, (state.buttons >> i) & 1U);
    for (int i = 0; i < 4; ++i)
        SDL_JoystickSetVirtualAxis(touchJoystick, i, comboandroid::axisToSdl(state.axes[i]));
    for (int i = 4; i < 6; ++i)
        SDL_JoystickSetVirtualAxis(touchJoystick, i, comboandroid::axisToSdl(state.axes[i] * 2.f - 1.f));
    if (quitRequested.exchange(false)) { SDL_Event event{}; event.type = SDL_QUIT; SDL_PushEvent(&event); }
}
void attachTouch() {
    SDL_SetMainReady();
    if (SDL_InitSubSystem(SDL_INIT_GAMECONTROLLER | SDL_INIT_EVENTS) != 0) throw std::runtime_error(SDL_GetError());
    SDL_VirtualJoystickDesc desc{};
    desc.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
    desc.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
    desc.naxes = SDL_CONTROLLER_AXIS_MAX; desc.nbuttons = SDL_CONTROLLER_BUTTON_MAX;
    desc.vendor_id = 0x1209; desc.product_id = 0x434f;
    desc.button_mask = (1U << SDL_CONTROLLER_BUTTON_MAX) - 1U;
    desc.axis_mask = (1U << SDL_CONTROLLER_AXIS_MAX) - 1U;
    desc.name = "ComboShip Android Touch"; desc.Update = updateTouch;
    const int index = SDL_JoystickAttachVirtualEx(&desc);
    if (index < 0) throw std::runtime_error(SDL_GetError());
    touchJoystick = SDL_JoystickOpen(index);
    if (!touchJoystick) throw std::runtime_error(SDL_GetError());
    updateTouch(nullptr);
}
void writeError(const std::filesystem::path& root, const std::string& error) {
    __android_log_print(ANDROID_LOG_ERROR, "ComboShip", "%s", error.c_str());
    if (!root.empty()) std::ofstream(root / "last-startup-error.txt") << error << '\n';
}
}
extern "C" JNIEXPORT void JNICALL
Java_org_comboship_android_GameActivity_nativePad(JNIEnv*, jclass, jint buttons,
        jfloat lx, jfloat ly, jfloat rx, jfloat ry, jfloat lt, jfloat rt) {
    input.set(static_cast<uint32_t>(buttons), {lx, ly, rx, ry, lt, rt});
}
extern "C" JNIEXPORT void JNICALL Java_org_comboship_android_GameActivity_nativeMenu(JNIEnv*, jclass) { Ship::Mobile::RequestMenu(); }
extern "C" JNIEXPORT jboolean JNICALL Java_org_comboship_android_GameActivity_nativeControlsSuppressed(JNIEnv*, jclass) {
    return !gameplayActive.load() || Ship::Mobile::IsMenuVisible();
}
extern "C" JNIEXPORT void JNICALL Java_org_comboship_android_GameActivity_nativeMenuScale(JNIEnv*, jclass, jfloat scale) {
    Ship::Mobile::RequestMenuScale(scale);
}
void ComboAndroid_SetGameplayActive(bool active) { gameplayActive = active; }
extern "C" JNIEXPORT void JNICALL Java_org_comboship_android_GameActivity_nativeRelease(JNIEnv*, jclass) { input.releaseAll(); }
extern "C" JNIEXPORT void JNICALL Java_org_comboship_android_GameActivity_nativeQuit(JNIEnv*, jclass) { quitRequested = true; }
extern "C" __attribute__((visibility("default"))) int SDL_main(int argc, char** argv) {
    std::filesystem::path root;
    try {
        if (argc != 4) throw std::runtime_error("Expected data directory, native-library directory, and recovery flag.");
        root = comboandroid::requireDirectory(argv[1]);
        const auto native = comboandroid::requireDirectory(argv[2]);
        comboandroid::SessionLock session(root);
        if (setenv("COMBOSHIP_SKIP_MODS", std::string(argv[3]) == "safe" ? "1" : "0", 1))
            throw std::runtime_error("Cannot initialize recovery mode.");
        if (setenv("COMBOSHIP_DATA_DIR", root.c_str(), 1) || setenv("COMBOSHIP_NATIVE_LIB_DIR", native.c_str(), 1) || chdir(root.c_str()))
            throw std::runtime_error("Cannot initialize the application data directory.");
        std::error_code ec;
        std::filesystem::remove(root / "last-startup-error.txt", ec);
        std::filesystem::rename(root / "native.log", root / "native.previous.log", ec);
        if (!freopen((root / "native.log").c_str(), "a", stdout)) throw std::runtime_error("Cannot open native.log");
        if (!freopen((root / "native.log").c_str(), "a", stderr)) throw std::runtime_error("Cannot redirect native errors");
        setvbuf(stdout, nullptr, _IOLBF, 0); setvbuf(stderr, nullptr, _IONBF, 0);
        SDL_SetHint(SDL_HINT_ANDROID_BLOCK_ON_PAUSE, "1");
        SDL_SetHint(SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "0");
        SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight");
        attachTouch();
        std::ofstream(root / ".running") << "Native session started\n";
        const int result = ComboAndroid_RunDesktopMain(1, argv);
        input.releaseAll(); touchJoystick = nullptr;
        if (result == 0) std::filesystem::remove(root / ".running", ec);
        else writeError(root, "ComboShip exited with status " + std::to_string(result) + ". See native.log.");
        return result;
    } catch (const std::exception& error) { writeError(root, error.what()); return 1; }
}
