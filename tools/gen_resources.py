#!/usr/bin/env python3
"""
Generates every JSON/PNG resource for Material Generators.
Run from the project root:   python3 tools/gen_resources.py
(needs Pillow:  pip install pillow)

To add a material: add a row to GENERATORS below AND to GeneratorDef.java.
"""
import json, os, random
from PIL import Image, ImageDraw

MOD = "material_generators"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main")
ASSETS = os.path.join(ROOT, "resources", "assets", MOD)
DATA = os.path.join(ROOT, "resources", "data", MOD)

# id, display name, color, recipe corner item, recipe edge item, required mod (or None)
GENERATORS = [
    ("zinc",         "Zinc",         (150, 172, 160), "create:zinc_ingot",        "create:andesite_alloy", "create"),
    ("andesite_alloy", "Andesite Alloy", (120, 116, 108), "create:andesite_alloy", "create:andesite_casing", "create"),
    ("copper",       "Copper",       (205, 118, 80),  "minecraft:copper_ingot", "minecraft:copper_block", None),
    ("iron",         "Iron",         (214, 214, 220), "minecraft:iron_ingot", "minecraft:iron_block", None),
    ("gold",         "Gold",         (250, 214, 64),  "minecraft:gold_ingot", "minecraft:gold_block", None),
    ("diamond",      "Diamond",      (90, 236, 226),  "minecraft:diamond", "minecraft:diamond_block", None),
    ("emerald",      "Emerald",      (60, 214, 110),  "minecraft:emerald", "minecraft:emerald_block", None),
    ("netherite",    "Netherite",    (146, 120, 134), "minecraft:netherite_ingot", "minecraft:diamond_block", None),
    ("lapis",        "Lapis",        (56, 100, 224),  "minecraft:lapis_lazuli", "minecraft:lapis_block", None),
    ("redstone",     "Redstone",     (222, 42, 38),   "minecraft:redstone", "minecraft:redstone_block", None),
    ("coal",         "Coal",         (96, 96, 108),   "minecraft:coal", "minecraft:coal_block", None),
    ("glowstone",    "Glowstone",    (255, 206, 96),  "minecraft:glowstone_dust", "minecraft:glowstone", None),
    ("amethyst",     "Amethyst",     (176, 120, 240), "minecraft:amethyst_shard", "minecraft:amethyst_block", None),
    ("dripstone",    "Dripstone",    (160, 124, 98),  "minecraft:pointed_dripstone", "minecraft:dripstone_block", None),
    ("stone",        "Stone",        (150, 150, 156), "minecraft:cobblestone", "minecraft:stone", None),
    ("obsidian",     "Obsidian",     (104, 72, 168),  "minecraft:crying_obsidian", "minecraft:obsidian", None),
    ("blaze_rod",    "Blaze Rod",    (240, 170, 30),  "minecraft:blaze_powder", "minecraft:blaze_rod", None),
    ("gunpowder",    "Gunpowder",    (140, 140, 152), "minecraft:gunpowder", "minecraft:tnt", None),
    ("nether_star",  "Nether Star",  (245, 245, 150), "minecraft:wither_skeleton_skull", "minecraft:soul_sand", None),
    ("quartz",       "Quartz",       (235, 229, 220), "minecraft:quartz",         "minecraft:quartz_block", None),
    ("oak_log",      "Oak Log",      (162, 130, 78),  "minecraft:oak_sapling",    "minecraft:oak_log", None),
    ("spruce_log",   "Spruce Log",   (122, 92, 54),   "minecraft:spruce_sapling", "minecraft:spruce_log", None),
    ("birch_log",    "Birch Log",    (216, 205, 152), "minecraft:birch_sapling",  "minecraft:birch_log", None),
    ("jungle_log",   "Jungle Log",   (149, 108, 76),  "minecraft:jungle_sapling", "minecraft:jungle_log", None),
    ("acacia_log",   "Acacia Log",   (168, 90, 50),   "minecraft:acacia_sapling", "minecraft:acacia_log", None),
    ("dark_oak_log", "Dark Oak Log", (98, 68, 36),    "minecraft:dark_oak_sapling", "minecraft:dark_oak_log", None),
    ("mangrove_log", "Mangrove Log", (146, 70, 62),   "minecraft:mangrove_propagule", "minecraft:mangrove_log", None),
    ("cherry_log",   "Cherry Log",   (226, 177, 176), "minecraft:cherry_sapling", "minecraft:cherry_log", None),
    ("crimson_stem", "Crimson Stem", (156, 56, 100),  "minecraft:crimson_fungus", "minecraft:crimson_stem", None),
    ("warped_stem",  "Warped Stem",  (43, 120, 118),  "minecraft:warped_fungus",  "minecraft:warped_stem", None),
]

def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        if isinstance(content, str):
            f.write(content)
        else:
            json.dump(content, f, indent=2)
            f.write("\n")

def clamp(v): return max(0, min(255, int(v)))
def shade(c, d): return tuple(clamp(x + d) for x in c)

# ---------------------------------------------------------------- textures
# The 32x32 block art is drawn by tools/pixel_art.py
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pixel_art
import gui_art
import zipfile

# which picture each generator gets (everything not listed here is a log end)
ICON_OF = {"zinc": "ingot", "andesite_alloy": "cog", "quartz": "crystal", "blaze_rod": "rod",
           "gunpowder": "dust", "nether_star": "star",
           "copper": "ingot", "iron": "ingot", "gold": "ingot", "netherite": "ingot",
           "diamond": "gem", "emerald": "gem", "lapis": "lump", "coal": "lump",
           "redstone": "dust", "glowstone": "dust", "amethyst": "crystal",
           "dripstone": "spike", "stone": "cube", "obsidian": "cube"}
BARK = {"oak_log": (88, 70, 40), "spruce_log": (58, 38, 18), "birch_log": (226, 226, 220),
        "jungle_log": (88, 66, 30), "acacia_log": (104, 100, 94), "dark_oak_log": (38, 28, 14),
        "mangrove_log": (84, 40, 36), "cherry_log": (74, 52, 58), "crimson_stem": (96, 30, 48),
        "warped_stem": (30, 78, 84)}

# Upgrade recipes: tier N is made from tier N-1 in the middle, surrounded by (corner item, edge item).
TIER_UP = {
    2: ("minecraft:gold_block", "minecraft:iron_block"),
    3: ("minecraft:diamond_block", "minecraft:gold_block"),
    4: ("minecraft:netherite_ingot", "minecraft:diamond_block"),
    5: ("minecraft:netherite_block", "minecraft:nether_star"),
}

def make_block(name, kind, base, tier, seed, extra=None, plate_base=None):
    pixel_art.make_block_textures(os.path.join(ASSETS, "textures", "block"), name, kind, base, tier, seed,
                                  extra, plate_base)

def cube_model(name):
    return {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"{MOD}:block/{name}_top", "bottom": f"{MOD}:block/{name}_bottom", "side": f"{MOD}:block/{name}"}}

def loot(name):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0, "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }

def register_block(name, display, texture, lang, blocks):
    write(f"{ASSETS}/blockstates/{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
    write(f"{ASSETS}/models/block/{name}.json", cube_model(texture))
    write(f"{ASSETS}/models/item/{name}.json", {"parent": f"{MOD}:block/{name}"})
    write(f"{DATA}/loot_tables/blocks/{name}.json", loot(name))
    lang[f"block.{MOD}.{name}"] = display
    blocks.append(f"{MOD}:{name}")

def build_black_pack():
    """Writes dist/Material_Generators_Black_GUI.zip (a normal resource pack)."""
    dist = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dist")
    work = os.path.join(dist, "black_pack")
    base = os.path.join(work, "assets", MOD)
    gui_art.make_all(os.path.join(base, "textures", "gui"), gui_art.BLACK)
    write(os.path.join(base, "gui_theme.json"), gui_art.BLACK["theme_json"])
    write(os.path.join(work, "pack.mcmeta"), {"pack": {
        "description": "Material Generators - Black GUI", "pack_format": 15}})
    zip_path = os.path.join(dist, "Material_Generators_Black_GUI.zip")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for folder, _, files in os.walk(work):
            for f in files:
                full = os.path.join(folder, f)
                z.write(full, os.path.relpath(full, work))

def main():
    lang = {f"itemGroup.{MOD}": "Material Generators"}
    blocks = []

    # --- generator core ---
    make_block("generator_core", "core", (200, 60, 50), 1, 999)
    register_block("generator_core", "Generator Core", "generator_core", lang, blocks)
    # Core = 8 iron ingots around the core from the original Tiered Generators mod.
    # If that mod is not installed, fall back to redstone so this mod stays usable.
    ORIGINAL = "tiered_generators"
    write(f"{DATA}/recipes/generator_core.json", {
        "conditions": [{"type": "forge:mod_loaded", "modid": ORIGINAL}],
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["aaa", "aba", "aaa"],
        "key": {"a": {"item": "minecraft:iron_ingot"}, "b": {"item": f"{ORIGINAL}:generator_core"}},
        "result": {"item": f"{MOD}:generator_core", "count": 1},
    })
    write(f"{DATA}/recipes/generator_core_fallback.json", {
        "conditions": [{"type": "forge:not", "value": {"type": "forge:mod_loaded", "modid": ORIGINAL}}],
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["aaa", "aba", "aaa"],
        "key": {"a": {"item": "minecraft:iron_ingot"}, "b": {"item": "minecraft:redstone"}},
        "result": {"item": f"{MOD}:generator_core", "count": 1},
    })

    # --- generators: tiers I-V ---
    TIER_NAME = ["", " Tier II", " Tier III", " Tier IV", " Tier V"]
    for i, (gid, disp, color, corner, edge, req) in enumerate(GENERATORS):
        icon = ICON_OF.get(gid, "log")
        extra = BARK.get(gid, shade(color, -70)) if icon == "log" else None
        cond = {"conditions": [{"type": "forge:mod_loaded", "modid": req}]} if req else {}
        names = [f"{gid}_generator", f"{gid}_generator_tier_ii", f"{gid}_generator_tier_iii",
                 f"{gid}_generator_tier_iv", f"{gid}_generator_tier_v"]
        for tier in range(1, 6):
            name = names[tier - 1]
            make_block(name, icon, color, tier, 100 + i * 10 + tier * 37, extra)
            register_block(name, f"{disp} Generator{TIER_NAME[tier - 1]}", name, lang, blocks)
            if tier == 1:
                a_item, b_item, c_item = corner, edge, f"{MOD}:generator_core"
            else:
                a_item, b_item = TIER_UP[tier]
                c_item = f"{MOD}:{names[tier - 2]}"
            recipe = dict(cond)
            recipe.update({
                "type": "minecraft:crafting_shaped", "category": "misc",
                "pattern": ["aba", "bcb", "aba"],
                "key": {"a": {"item": a_item}, "b": {"item": b_item}, "c": {"item": c_item}},
                "result": {"item": f"{MOD}:{name}", "count": 1},
            })
            write(f"{DATA}/recipes/{name}.json", recipe)

    # --- duplicator ---
    make_block("duplicator", "copy", (190, 110, 255), 2, 4242, plate_base=(70, 50, 98))
    register_block("duplicator", "Duplicator", "duplicator", lang, blocks)
    write(f"{DATA}/recipes/duplicator.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["aba", "bcb", "aba"],
        "key": {"a": {"item": "minecraft:diamond_block"}, "b": {"item": "minecraft:obsidian"},
                "c": {"item": "minecraft:nether_star"}},
        "result": {"item": f"{MOD}:duplicator", "count": 1},
    })

    # --- generator hub ---
    make_block("generator_hub", "hub", (120, 205, 255), 3, 9191, plate_base=(40, 56, 74))
    register_block("generator_hub", "Generator Hub", "generator_hub", lang, blocks)
    # Breaking a hub drops ONE item that carries every installed generator (with its items) and the expansions:
    # copy the block entity's bays and bay count into the dropped item's BlockEntityTag.
    write(f"{DATA}/loot_tables/blocks/generator_hub.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0, "bonus_rolls": 0.0,
            "entries": [{
                "type": "minecraft:item", "name": f"{MOD}:generator_hub",
                "functions": [{
                    "function": "minecraft:copy_nbt", "source": "block_entity",
                    "ops": [
                        {"source": "Bays", "target": "BlockEntityTag.Bays", "op": "replace"},
                        {"source": "BayCount", "target": "BlockEntityTag.BayCount", "op": "replace"},
                    ],
                }],
            }],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    write(f"{DATA}/recipes/generator_hub.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["aba", "bcb", "aba"],
        "key": {"a": {"item": "minecraft:diamond"}, "b": {"item": "minecraft:obsidian"},
                "c": {"item": f"{MOD}:generator_core"}},
        "result": {"item": f"{MOD}:generator_hub", "count": 1},
    })

    # --- hub expansion item ---
    pixel_art.make_expansion_icon(os.path.join(ASSETS, "textures", "item", "hub_expansion.png"))
    write(f"{ASSETS}/models/item/hub_expansion.json",
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/hub_expansion"}})
    lang[f"item.{MOD}.hub_expansion"] = "Hub Expansion"
    write(f"{DATA}/recipes/hub_expansion.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["aba", "bcb", "aba"],
        "key": {"a": {"item": "minecraft:diamond_block"}, "b": {"item": "minecraft:obsidian"},
                "c": {"item": f"{MOD}:generator_core"}},
        "result": {"item": f"{MOD}:hub_expansion", "count": 1},
    })

    # --- enchanter ---
    make_block("enchanter", "tome", (150, 110, 255), 3, 7777, plate_base=(50, 40, 90))
    register_block("enchanter", "Enchanter", "enchanter", lang, blocks)
    write(f"{DATA}/recipes/enchanter.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["aba", "bcb", "aba"],
        "key": {"a": {"item": "minecraft:diamond_block"}, "b": {"item": "minecraft:bookshelf"},
                "c": {"item": "minecraft:enchanting_table"}},
        "result": {"item": f"{MOD}:enchanter", "count": 1},
    })

    # --- GUIs: light theme inside the mod, black theme as a separate resource pack ---
    gui_art.make_all(os.path.join(ASSETS, "textures", "gui"), gui_art.LIGHT)
    write(os.path.join(ASSETS, "gui_theme.json"), gui_art.LIGHT["theme_json"])
    build_black_pack()

    write(os.path.join(ROOT, "resources", "assets", MOD, "lang", "en_us.json"), lang)
    write(os.path.join(ROOT, "resources", "data", "minecraft", "tags", "blocks", "mineable", "pickaxe.json"),
          {"replace": False, "values": blocks})
    print(f"Generated {len(blocks)} blocks.")

if __name__ == "__main__":
    main()
