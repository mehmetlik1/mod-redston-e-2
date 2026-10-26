"""
GUI textures for Material Generators, in two themes:
  LIGHT - the normal look shipped inside the mod
  BLACK - the optional "Black GUI" resource pack

Slot / button positions here must match the Java menus and screens:
  generator.png  176x250 panel, 44x16 buttons at x=176 (normal y=0, hover y=16, disabled y=32),
                 18x18 icon buttons at x=176 (normal y=48, hover y=66, disabled y=84)
  duplicator.png 176x166 panel, filled progress arrow at (176,0), 44x16 buttons at (176,20) normal / (176,36) hover
  generator_hub.png 176x250 panel like generator.png + bay frame at (146,16), tab sprites 26x24 at x=176 (normal y=104, hover y=130, selected y=156),
                    scroll arrows 26x12: up (176/202,182) down (176/202,196) = normal/hover,
                    26x16 buttons at y=210: normal x=176, hover x=202, disabled x=228
  enchanter.png  256x230 panel, sprites at y=230: big buttons (0/56/112), small buttons (168/180/192)
"""
import os

from PIL import Image, ImageDraw

LIGHT = dict(
    panel=(198, 198, 198), hi=(255, 255, 255), lo=(85, 85, 85), edge=None,
    slot=(139, 139, 139), slot_dark=(55, 55, 55), slot_light=(255, 255, 255),
    inset=(158, 158, 158), inset_dark=(85, 85, 85), inset_light=(240, 240, 240),
    btn=(176, 176, 176), btn_hi=(255, 255, 255), btn_lo=(70, 70, 70),
    btn_hover=(160, 184, 226), btn_off=(140, 140, 140),
    gold=(214, 170, 50), arrow_empty=(112, 112, 112), arrow_fill=(140, 70, 210),
    theme_json=dict(text="#404040", dim="#6E6E6E", accent="#1F7A2E", good="#1F7A2E", bad="#B3261E",
                    row_a="#1A000000", row_b="#0A000000", row_selected="#5545A045", bar_bg="#FF373737"),
)

BLACK = dict(
    panel=(22, 22, 26), hi=(84, 84, 98), lo=(0, 0, 0), edge=(0, 0, 0),
    slot=(10, 10, 12), slot_dark=(0, 0, 0), slot_light=(66, 66, 78),
    inset=(12, 12, 15), inset_dark=(0, 0, 0), inset_light=(58, 58, 68),
    btn=(44, 44, 54), btn_hi=(104, 104, 120), btn_lo=(6, 6, 8),
    btn_hover=(66, 66, 104), btn_off=(26, 26, 30),
    gold=(214, 170, 50), arrow_empty=(56, 56, 66), arrow_fill=(176, 116, 255),
    theme_json=dict(text="#E6E6EE", dim="#8C8C9C", accent="#7CFFB0", good="#5CFF8A", bad="#FF6B6B",
                    row_a="#22FFFFFF", row_b="#0AFFFFFF", row_selected="#5540C078", bar_bg="#FF08080A"),
)

THEMES = {"light": LIGHT, "black": BLACK}


def _c(rgb):
    return tuple(rgb) + (255,)


def _panel(d, w, h, t):
    d.rectangle([0, 0, w - 1, h - 1], fill=_c(t["panel"]))
    d.line([(0, 0), (w - 2, 0)], fill=_c(t["hi"]))
    d.line([(0, 0), (0, h - 2)], fill=_c(t["hi"]))
    d.line([(1, h - 1), (w - 1, h - 1)], fill=_c(t["lo"]))
    d.line([(w - 1, 1), (w - 1, h - 1)], fill=_c(t["lo"]))
    if t["edge"]:
        d.rectangle([0, 0, w - 1, h - 1], outline=_c(t["edge"]))
        d.line([(1, 1), (w - 3, 1)], fill=_c(t["hi"]))
        d.line([(1, 1), (1, h - 3)], fill=_c(t["hi"]))


def _slot(d, x, y, t, border=None):
    d.rectangle([x, y, x + 17, y + 17], fill=_c(t["slot"]))
    dark = border or t["slot_dark"]
    d.line([(x, y), (x + 17, y)], fill=_c(dark))
    d.line([(x, y), (x, y + 17)], fill=_c(dark))
    d.line([(x + 1, y + 17), (x + 17, y + 17)], fill=_c(t["slot_light"]))
    d.line([(x + 17, y + 1), (x + 17, y + 17)], fill=_c(t["slot_light"]))


def _inset(d, x0, y0, x1, y1, t):
    d.rectangle([x0, y0, x1, y1], fill=_c(t["inset"]))
    d.line([(x0, y0), (x1, y0)], fill=_c(t["inset_dark"]))
    d.line([(x0, y0), (x0, y1)], fill=_c(t["inset_dark"]))
    d.line([(x0 + 1, y1), (x1, y1)], fill=_c(t["inset_light"]))
    d.line([(x1, y0 + 1), (x1, y1)], fill=_c(t["inset_light"]))


def _button(d, x, y, w, h, t, state="normal"):
    fill = {"normal": t["btn"], "hover": t["btn_hover"], "off": t["btn_off"]}[state]
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=_c(fill))
    if state == "off":
        d.rectangle([x, y, x + w - 1, y + h - 1], outline=_c(t["inset_dark"]))
        return
    d.line([(x, y), (x + w - 2, y)], fill=_c(t["btn_hi"]))
    d.line([(x, y), (x, y + h - 2)], fill=_c(t["btn_hi"]))
    d.line([(x + 1, y + h - 1), (x + w - 1, y + h - 1)], fill=_c(t["btn_lo"]))
    d.line([(x + w - 1, y + 1), (x + w - 1, y + h - 1)], fill=_c(t["btn_lo"]))


def _sheet():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def make_generator_gui(path, t):
    img, d = _sheet()
    _panel(d, 176, 250, t)
    _inset(d, 7, 15, 168, 41, t)                       # toolbar: buttons + progress bar
    for r in range(6):
        for c in range(9):
            _slot(d, 7 + c * 18, 45 + r * 18, t)       # generated items
    for r in range(3):
        for c in range(9):
            _slot(d, 7 + c * 18, 167 + r * 18, t)      # player inventory
    for c in range(9):
        _slot(d, 7 + c * 18, 225, t)                   # hotbar
    for i, state in enumerate(("normal", "hover", "off")):
        _button(d, 176, i * 16, 44, 16, t, state)
        _button(d, 176, 48 + i * 18, 18, 18, t, state)
    _save(img, path)


def make_duplicator_gui(path, t):
    img, d = _sheet()
    _panel(d, 176, 166, t)
    _slot(d, 25, 34, t, t["gold"])                     # the item to copy
    d.rectangle([24, 33, 43, 52], outline=_c(t["gold"]))
    for r in range(3):
        for c in range(3):
            _slot(d, 97 + c * 18, 16 + r * 18, t)      # copies
    for r in range(3):
        for c in range(9):
            _slot(d, 7 + c * 18, 83 + r * 18, t)
    for c in range(9):
        _slot(d, 7 + c * 18, 141, t)

    def arrow(ox, oy, color):
        d.rectangle([ox, oy + 6, ox + 15, oy + 10], fill=_c(color))
        d.polygon([(ox + 14, oy + 1), (ox + 23, oy + 8), (ox + 14, oy + 15)], fill=_c(color))

    arrow(56, 34, t["arrow_empty"])
    arrow(176, 0, t["arrow_fill"])
    _button(d, 176, 20, 44, 16, t, "normal")
    _button(d, 176, 36, 44, 16, t, "hover")
    _save(img, path)


def make_enchanter_gui(path, t):
    img, d = _sheet()
    _panel(d, 256, 230, t)
    _inset(d, 7, 17, 65, 129, t)                       # left column
    _inset(d, 71, 17, 239, 130, t)                     # enchantment list
    _inset(d, 240, 17, 247, 130, t)                    # scroll track
    _slot(d, 25, 29, t, t["gold"])                     # item
    d.rectangle([24, 28, 43, 47], outline=_c(t["gold"]))
    _slot(d, 25, 63, t, (96, 220, 230))                # payment
    for r in range(3):
        for c in range(9):
            _slot(d, 47 + c * 18, 145 + r * 18, t)
    for c in range(9):
        _slot(d, 47 + c * 18, 203, t)
    for i, state in enumerate(("normal", "hover", "off")):
        _button(d, i * 56, 230, 56, 16, t, state)
        _button(d, 168 + i * 12, 230, 12, 12, t, state)
    _save(img, path)


def _tab(d, x, y, t, state):
    fill = {"normal": t["btn"], "hover": t["btn_hover"], "selected": t["panel"]}[state]
    d.rectangle([x, y, x + 25, y + 23], fill=_c(fill))
    d.line([(x, y), (x + 25, y)], fill=_c(t["btn_hi"]))
    d.line([(x, y), (x, y + 23)], fill=_c(t["btn_hi"]))
    d.line([(x + 1, y + 23), (x + 25, y + 23)], fill=_c(t["btn_lo"]))
    d.line([(x + 25, y + 1), (x + 25, y + 23)], fill=_c(t["btn_lo"]))
    if state == "selected":
        d.rectangle([x, y + 1, x + 1, y + 22], fill=_c(t["panel"]))     # opens into the panel
        d.line([(x + 3, y + 2), (x + 3, y + 21)], fill=_c(t["gold"]))
        d.line([(x + 4, y + 2), (x + 4, y + 21)], fill=_c(t["gold"]))


def _arrow_button(d, x, y, t, up, hover):
    fill = t["btn_hover"] if hover else t["btn"]
    d.rectangle([x, y, x + 25, y + 11], fill=_c(fill))
    d.line([(x, y), (x + 24, y)], fill=_c(t["btn_hi"]))
    d.line([(x, y), (x, y + 10)], fill=_c(t["btn_hi"]))
    d.line([(x + 1, y + 11), (x + 25, y + 11)], fill=_c(t["btn_lo"]))
    d.line([(x + 25, y + 1), (x + 25, y + 11)], fill=_c(t["btn_lo"]))
    cx, cy = x + 13, y + 6
    glyph = t["theme_json"]["text"].lstrip("#")
    col = tuple(int(glyph[i:i + 2], 16) for i in (0, 2, 4))
    if up:
        d.polygon([(cx - 5, cy + 2), (cx + 5, cy + 2), (cx, cy - 3)], fill=_c(col))
    else:
        d.polygon([(cx - 5, cy - 2), (cx + 5, cy - 2), (cx, cy + 3)], fill=_c(col))


def make_hub_gui(path, t):
    """Same panel as the generator, plus a frame for the bay's generator and the bay tab sprites."""
    img, d = _sheet()
    _panel(d, 176, 250, t)
    _inset(d, 7, 15, 168, 41, t)
    for r in range(6):
        for c in range(9):
            _slot(d, 7 + c * 18, 45 + r * 18, t)
    for r in range(3):
        for c in range(9):
            _slot(d, 7 + c * 18, 167 + r * 18, t)
    for c in range(9):
        _slot(d, 7 + c * 18, 225, t)
    _slot(d, 146, 16, t, t["gold"])                    # frame showing the generator in this bay
    for i, state in enumerate(("normal", "hover", "off")):
        _button(d, 176, i * 16, 44, 16, t, state)
        _button(d, 176, 48 + i * 18, 18, 18, t, state)
    _tab(d, 176, 104, t, "normal")
    _tab(d, 176, 130, t, "hover")
    _tab(d, 176, 156, t, "selected")
    _arrow_button(d, 176, 182, t, True, False)
    _arrow_button(d, 202, 182, t, True, True)
    _arrow_button(d, 176, 196, t, False, False)
    _arrow_button(d, 202, 196, t, False, True)
    _button(d, 176, 210, 26, 16, t, "normal")       # the "all generators" button: normal / hover / disabled
    _button(d, 202, 210, 26, 16, t, "hover")
    _button(d, 228, 210, 26, 16, t, "off")
    _save(img, path)


def _save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def make_all(directory, t):
    """Writes the three GUI textures into <directory>."""
    make_generator_gui(os.path.join(directory, "generator.png"), t)
    make_duplicator_gui(os.path.join(directory, "duplicator.png"), t)
    make_enchanter_gui(os.path.join(directory, "enchanter.png"), t)
    make_hub_gui(os.path.join(directory, "generator_hub.png"), t)
