from pathlib import Path

# ------------------------------------------------------------
# Fix G_BG_1CYC's background texel-count calculation.
#
# IMPORTANT: interpreter.cpp contains the same old LoadBlock expression
# in both Gfxs2dexBgCopy and Gfxs2dexBg1cyc. Grandma's stories use the
# G_BG_1CYC path, so this patch deliberately scopes the replacement to
# Interpreter::Gfxs2dexBg1cyc instead of replacing the first occurrence
# in the file.
#
# uObjBg.imageW/imageH are u10.2 fixed-point values. Majora's Mask's
# Prerender_DrawBackground2D commonly stores them as width*4+1 and
# height*4+1. Multiplying the raw fixed-point values before shifting can
# therefore over-count pixels. For 320x240, 1281*961>>4 = 76940, while
# (1281>>2)*(961>>2) = 76800.
# ------------------------------------------------------------

path = Path("libultraship/src/fast/interpreter.cpp")
text = path.read_text()

fn_marker = "void Interpreter::Gfxs2dexBg1cyc(F3DuObjBg* bg)"
fn_start = text.find(fn_marker)
if fn_start == -1:
    raise SystemExit("ERROR: Could not locate Interpreter::Gfxs2dexBg1cyc")

fn_end = text.find("\nvoid Interpreter::", fn_start + len(fn_marker))
if fn_end == -1:
    fn_end = len(text)

bg1cyc = text[fn_start:fn_end]

old_load = """    GfxDpLoadBlock(G_TX_LOADTILE, 0, 0, (bg->b.imageW * bg->b.imageH >> 4) - 1, 0);
"""

new_load = """    // imageW/imageH are u10.2 fixed-point. Convert EACH dimension to pixels before multiplying.
    GfxDpLoadBlock(G_TX_LOADTILE, 0, 0, ((bg->b.imageW >> 2) * (bg->b.imageH >> 2)) - 1, 0);
"""

if old_load in bg1cyc:
    bg1cyc = bg1cyc.replace(old_load, new_load, 1)
elif "Convert EACH dimension to pixels before multiplying" in bg1cyc:
    print("G_BG_1CYC fixed-point texel-count fix already present in correct function.")
else:
    raise SystemExit("ERROR: Could not locate old LoadBlock expression inside Gfxs2dexBg1cyc")

# Build-time verification: do not silently succeed if the old expression is
# still present in the Grandma rendering function.
if "(bg->b.imageW * bg->b.imageH >> 4)" in bg1cyc:
    raise SystemExit("ERROR: Gfxs2dexBg1cyc still contains the old texel-count expression")
if "((bg->b.imageW >> 2) * (bg->b.imageH >> 2)) - 1" not in bg1cyc:
    raise SystemExit("ERROR: Gfxs2dexBg1cyc does not contain the corrected texel-count expression")

text = text[:fn_start] + bg1cyc + text[fn_end:]
path.write_text(text)
print("G_BG_1CYC texel-count fix applied to the correct function and verified.")

# ------------------------------------------------------------
# Keep the CI8 resource bounds check as a defensive second layer.
# Once the G_BG_1CYC load above is correct, Grandma's 320x240 CI8 image
# should request exactly 76,800 bytes, so this clamp should not fire.
# ------------------------------------------------------------

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
