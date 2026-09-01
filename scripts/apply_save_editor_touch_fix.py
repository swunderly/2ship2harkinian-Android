from pathlib import Path

path = Path("mm/2s2h/DeveloperTools/SaveEditor.cpp")
text = path.read_text()

old_constants = """constexpr float INV_GRID_WIDTH = 46.0f;
constexpr float INV_GRID_HEIGHT = 58.0f;
constexpr float INV_GRID_ICON_SIZE = 40.0f;
constexpr float INV_GRID_PADDING = 10.0f;
constexpr float INV_GRID_TOP_MARGIN = 20.0f;
"""

new_constants = """#if defined(__ANDROID__)
// Touch-friendly Save Editor sizing. The desktop values are too small to
// reliably select inventory, mask, quest, and dungeon icons on a phone/tablet.
constexpr float INV_GRID_WIDTH = 100.0f;
constexpr float INV_GRID_HEIGHT = 112.0f;
constexpr float INV_GRID_ICON_SIZE = 88.0f;
constexpr float INV_GRID_PADDING = 14.0f;
constexpr float INV_GRID_TOP_MARGIN = 24.0f;
constexpr float INV_GRID_AMMO_WIDTH = 44.0f;
constexpr int INV_PICKER_COLUMNS = 6;
#else
constexpr float INV_GRID_WIDTH = 46.0f;
constexpr float INV_GRID_HEIGHT = 58.0f;
constexpr float INV_GRID_ICON_SIZE = 40.0f;
constexpr float INV_GRID_PADDING = 10.0f;
constexpr float INV_GRID_TOP_MARGIN = 20.0f;
constexpr float INV_GRID_AMMO_WIDTH = 24.0f;
constexpr int INV_PICKER_COLUMNS = 8;
#endif
"""

if old_constants in text:
    text = text.replace(old_constants, new_constants, 1)
elif "INV_GRID_AMMO_WIDTH" not in text:
    raise SystemExit("ERROR: Could not locate Save Editor grid constants")

old_ammo = """    ImGui::SetCursorPos(
        ImVec2(x * INV_GRID_WIDTH + INV_GRID_PADDING + 7.0f,
               y * INV_GRID_HEIGHT + INV_GRID_TOP_MARGIN + INV_GRID_PADDING + (INV_GRID_ICON_SIZE - 2.0f)));
    ImGui::PushItemWidth(24.0f);
"""

new_ammo = """    const float ammoXOffset = (INV_GRID_ICON_SIZE - INV_GRID_AMMO_WIDTH) * 0.5f;
    ImGui::SetCursorPos(
        ImVec2(x * INV_GRID_WIDTH + INV_GRID_PADDING + ammoXOffset,
               y * INV_GRID_HEIGHT + INV_GRID_TOP_MARGIN + INV_GRID_PADDING + (INV_GRID_ICON_SIZE - 2.0f)));
    ImGui::PushItemWidth(INV_GRID_AMMO_WIDTH);
"""

if old_ammo in text:
    text = text.replace(old_ammo, new_ammo, 1)
elif "ammoXOffset" not in text:
    raise SystemExit("ERROR: Could not locate Save Editor ammo input layout")

old_wrap = """            if (((pickerIndex + 1) % 8) != 0) {
"""
new_wrap = """            if (((pickerIndex + 1) % INV_PICKER_COLUMNS) != 0) {
"""

if old_wrap in text:
    text = text.replace(old_wrap, new_wrap, 1)
elif "INV_PICKER_COLUMNS" not in text:
    raise SystemExit("ERROR: Could not locate inventory picker column layout")

path.write_text(text)
print("Applied Android touch-friendly Save Editor sizing.")

# Rename this test build so it is easy to distinguish from the previous
# Grandma-only test APK. Keep the same test package/data folder so the user's
# existing test save can be used without copying it again.
strings_path = Path("Android/app/src/main/res/values/strings.xml")
strings = strings_path.read_text()
strings = strings.replace(">2S2H Grandma Fix<", ">2S2H Touch Fix<")
strings_path.write_text(strings)
