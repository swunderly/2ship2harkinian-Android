#!/usr/bin/env python3
"""Package real ARM64 libraries and no-ROM support archives into the Android app."""
import argparse,hashlib,json,shutil,struct,zipfile
from pathlib import Path
REQUIRED={'libcomboship.so','libsoh.so','lib2ship.so','libcomboui.so','libultraship.so','libSDL2.so','libc++_shared.so'}
def arm64(data,name):
    if len(data)<64 or data[:6]!=b'\x7fELF\x02\x01' or struct.unpack_from('<H',data,18)[0]!=183:
        raise RuntimeError(f'{name} is not a little-endian ARM64 ELF library')
    offset=struct.unpack_from('<Q',data,32)[0]
    entry_size,count=struct.unpack_from('<HH',data,54)
    if entry_size<56 or offset+entry_size*count>len(data):raise RuntimeError(f'{name}: malformed program headers')
    for i in range(count):
        base=offset+i*entry_size
        if struct.unpack_from('<I',data,base)[0]==1:
            align=struct.unpack_from('<Q',data,base+48)[0]
            if align<16384:raise RuntimeError(f'{name}: PT_LOAD alignment {align} below 16 KiB')
def copy_tree(source,dest):
    if not source.is_dir():raise RuntimeError(f'Missing directory: {source}')
    shutil.copytree(source,dest,dirs_exist_ok=True)
def stage(upstream,native,sdl,ndk,app):
    assets=app/'src/main/assets';libs=app/'src/main/jniLibs/arm64-v8a';java=app/'src/main/sdl-java'
    for generated in (assets,libs,java):
        if generated.exists():shutil.rmtree(generated)
        generated.mkdir(parents=True)
    candidates={}
    for source in sorted(native.rglob('*.so')):
        if source.is_file():
            digest=hashlib.sha256(source.read_bytes()).hexdigest()
            if source.name in candidates and candidates[source.name][1]!=digest:raise RuntimeError(f'Conflicting libraries named {source.name}')
            candidates[source.name]=(source,digest)
    cxx=ndk/'toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so'
    candidates['libc++_shared.so']=(cxx,None)
    missing=REQUIRED-candidates.keys()
    if missing:raise RuntimeError(f'Missing real native game libraries: {sorted(missing)}')
    for name,(source,_) in candidates.items():
        arm64(source.read_bytes(),name);shutil.copy2(source,libs/name)
    copy_tree(sdl/'android-project/app/src/main/java',java)
    runtime=assets/'runtime';runtime.mkdir()
    for game,archive in [('soh','soh.o2r'),('mm','2ship.o2r')]:
        source=upstream/game/archive
        if not source.is_file():raise RuntimeError(f'Missing no-ROM archive {source}; run host archive generators')
        with zipfile.ZipFile(source) as z:
            if z.testzip() is not None:raise RuntimeError(f'Invalid support archive {source}')
            if game=='soh' and 'shaders/opengl/default.shader.glsl' not in z.namelist():raise RuntimeError('Missing renderer shader')
        shutil.copy2(source,runtime/archive)
        # Exactly match the native ComboShip POST_BUILD flat merged extractor layout.
        copy_tree(upstream/game/'assets/extractor',runtime/'assets')
        copy_tree(upstream/game/'assets/xml',runtime/'assets/xml')
    shutil.copy2(upstream/'gamecontrollerdb.txt',runtime/'gamecontrollerdb.txt')
    for path in runtime.rglob('*'):
        if not path.is_file():continue
        if path.suffix.lower() in {'.z64','.v64','.n64','.elf','.exe','.dll','.so'}:raise RuntimeError(f'Unexpected ROM/executable asset: {path}')
        if path.suffix.lower() in {'.o2r','.otr'} and path.name not in {'soh.o2r','2ship.o2r'}:raise RuntimeError(f'Game-derived archive must not ship: {path}')
    records=[{'path':p.relative_to(runtime).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(runtime.rglob('*')) if p.is_file()]
    (assets/'support-manifest.json').write_text(json.dumps({'version':1,'upstream':'94eb185e4abcc2d568aa8241fa02c43cdd86c439','files':records},indent=2)+'\n')
    notices=assets/'licenses';notices.mkdir()
    for root,label in ((upstream,'ComboShip'),(upstream/'libultraship','libultraship'),(upstream/'soh','Shipwright'),(upstream/'mm','2Ship'),(sdl,'SDL2')):
        for path in root.glob('*'):
            if path.is_file() and (path.name.upper().startswith('LICENSE') or path.name.upper().startswith('COPYING')):shutil.copy2(path,notices/(label+'-'+path.name))
    print(f'Staged {len(candidates)} ARM64 libraries and {len(records)} no-ROM support files.')
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('upstream','native','sdl','ndk','app'):p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();stage(a.upstream.resolve(),a.native.resolve(),a.sdl.resolve(),a.ndk.resolve(),a.app.resolve())
