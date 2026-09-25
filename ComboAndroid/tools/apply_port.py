#!/usr/bin/env python3
"""Apply narrow Android patches to a locked ComboShip checkout, never to game data."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
PIN = '94eb185e4abcc2d568aa8241fa02c43cdd86c439'
def replace(root, relative, old, new, count=1):
    path=root/relative
    text=path.read_text()
    actual=text.count(old)
    if actual != count: raise RuntimeError(f'{relative}: expected {count} patch anchors, found {actual}. Upstream drift.')
    path.write_text(text.replace(old,new))
def apply(root,overlay):
    marker=root/'.comboship-android-patched.json'
    if marker.exists(): raise RuntimeError('Already patched. Use a fresh checkout; modifications are never reset.')
    if not (root/'.git').exists(): raise RuntimeError('A pinned git checkout is required.')
    head=subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'],text=True).strip()
    if head != PIN: raise RuntimeError(f'Expected {PIN}, got {head}')
    if subprocess.check_output(['git','-C',str(root),'status','--porcelain','--untracked-files=no'],text=True).strip():
        raise RuntimeError('Refusing to patch tracked local modifications.')
    if (root/'ComboAndroid').exists(): raise RuntimeError('Overlay destination already exists.')
    shutil.copytree(overlay,root/'ComboAndroid',ignore=shutil.ignore_patterns('build','.gradle','.cxx','__pycache__','local.properties','*.apk'))
    for relative in ['CMakeLists.txt','soh/CMakeLists.txt','mm/CMakeLists.txt','OTRExporter/CMakeLists.txt']:
        replace(root,relative,'set(CMAKE_SYSTEM_VERSION 10.0 CACHE STRING "" FORCE)','if(WIN32)\n    set(CMAKE_SYSTEM_VERSION 10.0 CACHE STRING "" FORCE)\nendif()')
    replace(root,'CMakeLists.txt','# Shared libultraship (in Combo directory)','include("${CMAKE_SOURCE_DIR}/ComboAndroid/cmake/AndroidDependencies.cmake")\n\n# Shared libultraship (in Combo directory)')
    exporter='add_subdirectory(OTRExporter ${CMAKE_BINARY_DIR}/OTRExporter)'
    replace(root,'CMakeLists.txt',exporter,exporter+'\n# Exporter consumes engine XML/ZIP headers; inherit actual target dependencies.\ntarget_link_libraries(OTRExporter PUBLIC libultraship PNG::PNG)')
    replace(root,'combo/CMakeLists.txt','add_executable(ComboShip ${COMBO_SOURCES})','''if(ANDROID)
    add_library(ComboShip SHARED ${COMBO_SOURCES}
        ${CMAKE_SOURCE_DIR}/ComboAndroid/app/src/main/cpp/AndroidEntry.cpp
        ${CMAKE_SOURCE_DIR}/ComboAndroid/app/src/main/cpp/RuntimePaths.cpp)
    set_target_properties(ComboShip PROPERTIES OUTPUT_NAME comboship)
    set_target_properties(comboui PROPERTIES PREFIX "lib")
    target_link_libraries(ComboShip PRIVATE libultraship android log)
    target_link_options(ComboShip PRIVATE "-Wl,-z,global")
    target_link_options(libultraship PRIVATE "-Wl,-z,global")
    target_include_directories(ComboShip PRIVATE ${CMAKE_SOURCE_DIR}/ComboAndroid/app/src/main/cpp)
else()
    add_executable(ComboShip ${COMBO_SOURCES})
endif()''')
    replace(root,'combo/ComboShip.cpp','int main(int argc, char** argv) {','''#ifdef __ANDROID__
int ComboAndroid_RunDesktopMain(int argc, char** argv) {
#else
int main(int argc, char** argv) {
#endif''')
    replace(root,'combo/ComboShip.cpp',"    std::string path = std::strchr(name, '/') ? name : std::string(\"./\") + name;",'''#ifdef __ANDROID__
    const char* directory = std::getenv("COMBOSHIP_NATIVE_LIB_DIR");
    if (!directory || std::strchr(name, '/')) return nullptr;
    std::string path = std::string(directory) + "/" + name;
#else
    std::string path = std::strchr(name, '/') ? name : std::string("./") + name;
#endif''')
    replace(root,'combo/ComboShip.cpp','    const char* comboUiDll = "comboui.so";','''#ifdef __ANDROID__
    const char* comboUiDll = "libcomboui.so";
#else
    const char* comboUiDll = "comboui.so";
#endif''')
    for signature in ['std::string Context::GetAppBundlePath() {','std::string Context::GetAppDirectoryPath(const std::string& appName) {']:
        replace(root,'libultraship/src/ship/Context.cpp',signature,signature+'''
#if defined(__ANDROID__) && defined(COMBO_BUILD)
    const char* comboRoot = std::getenv("COMBOSHIP_DATA_DIR");
    if (comboRoot && comboRoot[0] == '/') return comboRoot;
#endif''')
    replace(root,'libultraship/src/fast/Fast3dWindow.cpp','#ifdef __linux__','#if defined(__linux__) && !defined(__ANDROID__)')
    replace(root,'libultraship/src/ship/port/mobile/MobileImpl.cpp','            state->ClearText();','            if (state) state->ClearText();')
    picker='combo/gui/ComboFilePicker.h'
    replace(root,picker,'#else\n#include "portable-file-dialogs.h"','#elif !defined(__ANDROID__)\n#include "portable-file-dialogs.h"')
    replace(root,picker,'#else\n    static const bool sAvailable','#elif defined(__ANDROID__)\n    return false; // Setup uses the Android document picker.\n#else\n    static const bool sAvailable')
    replace(root,picker,'#else\n    (void)winFilter;','#elif defined(__ANDROID__)\n    (void)title; (void)winFilter; (void)pfdFilters;\n    return {};\n#else\n    (void)winFilter;')
    for game in ['soh','mm']:
        for package in ['Ogg','Vorbis','Opus','OpusFile']:
            old=f'    find_package({package} REQUIRED)'
            new=f'    if(ANDROID)\n        find_package({package} CONFIG REQUIRED)\n    else()\n{old}\n    endif()'
            replace(root,f'{game}/CMakeLists.txt',old,new)
        with (root/game/'CMakeLists.txt').open('a') as out:
            out.write('''
if(ANDROID)
    target_compile_options(${PROJECT_NAME} PRIVATE
        $<$<COMPILE_LANGUAGE:C>:-Wno-incompatible-pointer-types;-Wno-int-conversion;-Wno-implicit-int>)
endif()
''')
    changed=subprocess.check_output(['git','-C',str(root),'diff','--name-only'],text=True).splitlines()
    manifest={'upstream':PIN,'patchedFiles':{name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in changed}}
    marker.write_text(json.dumps(manifest,indent=2)+'\n')
    print(f'Patched {len(changed)} upstream files; retained both games and combined randomizer.')
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('checkout',type=Path)
    parser.add_argument('--overlay',type=Path,default=Path(__file__).resolve().parents[1])
    args=parser.parse_args()
    apply(args.checkout.resolve(),args.overlay.resolve())
