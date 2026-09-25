"""Validate the Android loader contract directly from ELF dynamic sections."""
import struct

SYSTEM_LIBRARIES = {
    'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libandroid.so', 'libz.so',
    'libEGL.so', 'libGLESv1_CM.so', 'libGLESv2.so', 'libGLESv3.so',
    'libOpenSLES.so', 'libvulkan.so', 'libaaudio.so', 'libmediandk.so',
}
EXPORTS = {
    'libcomboship.so': {'SDL_main', 'Java_org_comboship_android_GameActivity_nativePad',
        'Java_org_comboship_android_GameActivity_nativeMenu',
        'Java_org_comboship_android_GameActivity_nativeRelease',
        'Java_org_comboship_android_GameActivity_nativeQuit',
        'Java_org_comboship_android_GameActivity_nativeControlsSuppressed',
        'Java_org_comboship_android_GameActivity_nativeMenuScale'},
    'libsoh.so': {'SOH_Init', 'SOH_RunMain', 'SOH_ResumeGame', 'SOH_StartExtraction'},
    'lib2ship.so': {'MM_InitArchives', 'MM_RunGame', 'MM_ResumeGame', 'MM_StartExtraction'},
    'libcomboui.so': {'ComboUI_Register', 'ComboUI_RunExtraction'},
}

def metadata(data):
    if data[:6] != b'\x7fELF\x02\x01':
        raise ValueError('Expected little-endian ELF64')
    offset = struct.unpack_from('<Q', data, 40)[0]
    size, count = struct.unpack_from('<HH', data, 58)
    if size < 64 or offset + size * count > len(data):
        raise ValueError('Invalid ELF section table')
    sections = [struct.unpack_from('<IIQQQQIIQQ', data, offset + i * size) for i in range(count)]
    def string(table, position):
        start, length = table[4], table[5]
        if position >= length or start + length > len(data):
            raise ValueError('Invalid ELF string offset')
        end = data.find(b'\0', start + position, start + length)
        if end < 0: raise ValueError('Unterminated ELF string')
        return data[start + position:end].decode('utf-8')
    needed, exports = set(), set()
    for section in sections:
        kind, start, length, link, stride = section[1], section[4], section[5], section[6], section[9]
        if kind not in (6, 11): continue
        if start + length > len(data) or link >= count or stride < (16 if kind == 6 else 24) or length % stride:
            raise ValueError('Invalid ELF dynamic section')
        for pos in range(start, start + length, stride):
            if kind == 6:
                tag, value = struct.unpack_from('<qQ', data, pos)
                if tag == 1: needed.add(string(sections[link], value))
            else:
                name, info, other, index, _, _ = struct.unpack_from('<IBBHQQ', data, pos)
                if index and info >> 4 in (1, 2) and other & 3 in (0, 3):
                    exports.add(string(sections[link], name))
    return needed, exports

def verify_runtime(libraries, requested_exports=None):
    for name, data in libraries.items():
        needed, exports = metadata(data)
        missing = needed - libraries.keys() - SYSTEM_LIBRARIES
        if missing: raise RuntimeError(f'{name}: missing packaged dependencies {sorted(missing)}')
        required = EXPORTS.get(name, set()) | set((requested_exports or {}).get(name, []))
        absent = required - exports
        if absent: raise RuntimeError(f'{name}: missing runtime entry points {sorted(absent)}')
