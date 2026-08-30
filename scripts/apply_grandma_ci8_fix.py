from pathlib import Path

# Fix G_BG_1CYC's texel count. uObjBg imageW/imageH are u10.2 fixed-point,
# so each dimension must be converted to pixels before multiplying.
path = Path("libultraship/src/fast/interpreter.cpp")
text = path.read_text()

marker = "void Interpreter::Gfxs2dexBg1cyc(F3DuObjBg* bg)"
start = text.find(marker)
if start == -1:
    raise SystemExit("ERROR: Could not locate Gfxs2dexBg1cyc")

end = text.find("\nvoid Interpreter::", start + len(marker))
if end == -1:
    end = len(text)

func = text[start:end]
old = "    GfxDpLoadBlock(G_TX_LOADTILE, 0, 0, (bg->b.imageW * bg->b.imageH >> 4) - 1, 0);\n"
new = "    GfxDpLoadBlock(G_TX_LOADTILE, 0, 0, ((bg->b.imageW >> 2) * (bg->b.imageH >> 2)) - 1, 0);\n"

if old in func:
    func = func.replace(old, new, 1)
elif new not in func:
    raise SystemExit("ERROR: Could not locate G_BG_1CYC LoadBlock expression")

text = text[:start] + func + text[end:]
path.write_text(text)
print("Applied G_BG_1CYC fixed-point texel-count fix.")

# Keep the test APK visually distinct from the normal installation.
strings_path = Path("Android/app/src/main/res/values/strings.xml")
strings = strings_path.read_text()
strings = strings.replace(">2 Ship 2 Harkinian<", ">2S2H Grandma Fix<")
strings_path.write_text(strings)

# Keep the test APK on a separate data directory.
activity_path = Path("Android/app/src/main/java/com/twoshipfork/mm/MainActivity.java")
activity = activity_path.read_text()
activity = activity.replace(
    'return new File(Environment.getExternalStorageDirectory(), "2S2H");',
    'return new File(Environment.getExternalStorageDirectory(), "2S2H-GrandmaFix");',
    1,
)
activity_path.write_text(activity)
