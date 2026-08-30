from pathlib import Path

# ------------------------------------------------------------
# Patch libultraship CI8 decoding to prevent Grandma's story
# from reading beyond the end of its background image.
# ------------------------------------------------------------

path = Path("libultraship/src/fast/interpreter.cpp")
text = path.read_text()

start = text.index("void Interpreter::ImportTextureCi8")
end = text.find("\nvoid Interpreter::", start + 1)

if end == -1:
    end = len(text)

func = text[start:end]

if "sourceSizeBytes" in func:
    print("Grandma CI8 bounds patch already present.")
else:
    size_point = """    uint32_t lineSizeBytes = mRdp->loaded_texture[mRdp->texture_tile[tile].tmem_index].line_size_bytes;
"""

    size_replacement = """    uint32_t lineSizeBytes = mRdp->loaded_texture[mRdp->texture_tile[tile].tmem_index].line_size_bytes;

    // Safety for CI8/S2DEX background loads such as Grandma's story.
    // Never permit the decoder to read beyond the actual O2R image resource.
    uint32_t sourceSizeBytes = sizeBytes;
    if (!importReplacement && metadata->resource != nullptr) {
        sourceSizeBytes = metadata->resource->ImageDataSize;

        if (sizeBytes > sourceSizeBytes) {
            SPDLOG_WARN(
                "CI8: requested {} bytes but resource contains only {}; clamping",
                sizeBytes, sourceSizeBytes);
            sizeBytes = sourceSizeBytes;
        }
    }
"""

    if size_point not in func:
        raise SystemExit("ERROR: Could not locate CI8 size variables")

    func = func.replace(size_point, size_replacement, 1)

    old_loop = """    for (uint32_t i = 0, j = 0; i < sizeBytes; j += fullImageLineSizeBytes - lineSizeBytes) {
        for (uint32_t k = 0; k < lineSizeBytes; i++, k++, j++) {
"""

    new_loop = """    for (uint32_t i = 0, j = 0;
         i < sizeBytes && j < sourceSizeBytes;
         j += fullImageLineSizeBytes - lineSizeBytes) {
        for (uint32_t k = 0;
             k < lineSizeBytes && i < sizeBytes && j < sourceSizeBytes;
             i++, k++, j++) {
"""

    if old_loop not in func:
        raise SystemExit("ERROR: Could not locate CI8 decoding loop")

    func = func.replace(old_loop, new_loop, 1)

    text = text[:start] + func + text[end:]
    path.write_text(text)

    print("Grandma CI8 bounds patch applied successfully.")

# ------------------------------------------------------------
# Rename the TEST app so it is visually distinguishable from
# the untouched official Linkzenic installation.
# ------------------------------------------------------------

strings_path = Path("Android/app/src/main/res/values/strings.xml")

if strings_path.exists():
    strings = strings_path.read_text()

    if ">2 Ship 2 Harkinian<" in strings:
        strings = strings.replace(
            ">2 Ship 2 Harkinian<",
            ">2S2H Grandma Fix<"
        )
        strings_path.write_text(strings)
        print("Test app renamed to 2S2H Grandma Fix.")
    else:
        print("App name already changed or did not match expected text.")

# ------------------------------------------------------------
# Give the patched test app its own data directory.
# This prevents it from touching the normal /2S2H folder.
# ------------------------------------------------------------

main_activity = Path(
    "Android/app/src/main/java/com/twoshipfork/mm/MainActivity.java"
)

main_text = main_activity.read_text()

old_default = '''return new File(Environment.getExternalStorageDirectory(), "2S2H");'''

new_default = '''return new File(Environment.getExternalStorageDirectory(), "2S2H-GrandmaFix");'''

if old_default in main_text:
    main_text = main_text.replace(old_default, new_default, 1)
    main_activity.write_text(main_text)
    print("Patched app data folder changed to /2S2H-GrandmaFix.")
elif '"2S2H-GrandmaFix"' in main_text:
    print("Patched app data folder already changed.")
else:
    raise SystemExit("ERROR: Could not locate default 2S2H data folder")
