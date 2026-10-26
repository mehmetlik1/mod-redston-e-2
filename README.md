# Material Generators (Forge 1.20.1)

Generator blocks that slowly fill their own inventory with a material.

**Materials (29 generators):** Zinc and Andesite Alloy (Create, optional), Blaze Rod, Gunpowder, Nether Star, Quartz,
Copper, Iron, Gold, Diamond, Emerald, Netherite, Lapis, Redstone, Coal, Glowstone, Amethyst, Dripstone,
Stone (switchable: Stone / Cobblestone / Deepslate), Obsidian (switchable: Obsidian / Crying Obsidian), and all
10 wood types. Tier I recipe: 4 of the material in the corners, 4 of its block (or a related
block) on the edges, a Generator Core in the middle. Create generators need Create installed.

* **Generator Core:** 8 iron ingots around the Generator Core from the original Tiered Generators mod (falls back to
  8 iron ingots around 1 redstone if that mod is missing).

## Tiers I - V
Every generator comes in five tiers. Tier N is crafted from tier N-1 (in the middle):

| Tier | Speed (default) | Surrounding items |
|---|---|---|
| I | 1 item / 20 ticks | material items + Generator Core |
| II | 1 / 10 ticks | 4 gold blocks + 4 iron blocks |
| III | 1 / 5 ticks | 4 diamond blocks + 4 gold blocks |
| IV | 1 / 2 ticks | 4 netherite ingots + 4 diamond blocks |
| V | 1 / tick | 4 netherite blocks + 4 nether stars |

Speeds are in `config/material_generators.toml`. Right-click a generator for its 54-slot GUI:
* **ON/OFF** switches it on or off.
* **Items / Blocks** switches between making the material and making its block (iron ingots -> iron blocks).
  Blocks take longer: x9 for ingot/gem blocks, x4 for quartz/glowstone/amethyst/dripstone, x2 for wood and
  stone bricks. Blaze Rod, Gunpowder and Nether Star have no block form, so that button is greyed out.
* **The item icon button** shows what the generator is making. On Stone and Obsidian generators, click it to
  switch material (Stone -> Cobblestone -> Deepslate, Obsidian <-> Crying Obsidian). In block mode, Stone makes
  stone bricks, Cobblestone makes mossy cobblestone, Deepslate makes deepslate bricks; obsidian has no block form.
* **Pick up** removes the block and gives you the generator item with all its items stored inside it
  (like a shulker box) - nothing drops. Placing it again restores them. A picked-up generator or Duplicator shows
  "Items inside: N" (and its biggest piles) in its tooltip, and a counter appears above your hotbar while you hold it.

## Open generators, the hub, the Duplicator and the Enchanter without placing them
**Sneak + right-click** with a generator, Generator Hub, Duplicator or Enchanter item in your hand (on a block or in
the air) opens its GUI right there instead of placing it. Everything works like the placed block (ON/OFF, Items/Blocks, material
switch, bays and tabs, copying, enchanting), except "Pick up" (it already is an item). The Enchanter hands your
item and payment back when you close it. While the GUI is open the generator or hub keeps
producing, and everything is saved back into the item when you close it. The item's slot is locked while its GUI is
open. Normal right-click still places the block. Generator and Hub items do not stack.

## Generators keep working while carried
A generator item (placed or not) keeps producing while you hold it in your main hand or offhand, as long as it is
switched ON. Items go straight into the item's stored contents (see the "Items inside" counter above your hotbar);
placing it later restores them.
By default only held generators work; set `work_anywhere_in_inventory = true` in the config to make every
generator in your inventory work. Generator and Duplicator items no longer stack (their contents live in the item).

## Generator Hub
* Recipe: 4 diamonds (corners), 4 obsidian (edges), 1 Generator Core (centre).
* One block with **6 bays to start** (upgradeable to 18, see below). Each bay holds one generator item (any material, any tier) and has its own pocket of
  54 slots, its own ON/OFF, Items/Blocks and material switch and its own timer. All bays run at the same time.
* Pick a bay with the tabs on the right of the GUI. To install a generator: click the gold frame (top right) while
  holding a generator on your cursor, or shift-click one from your inventory (goes into the first free bay).
  Click the frame with an empty cursor to take it out again.
* A generator keeps its items when it goes in or out (they are loaded into / packed back into the item).
* The small **Stop / Go** button in the toolbar stops every installed generator in the hub with one click (if any is
  running), or starts them all (if all are stopped). The ON/OFF button still switches only the selected bay.
* Hoppers and pipes can take items out of any bay's pocket (nothing can be inserted).
* **Hub Expansion** (crafted: 4 diamond blocks in the corners, 4 obsidian on the edges, 1 Generator Core in the
  centre): right-click a hub with it to add 2 bays, up to 18 bays (6 expansions). With more than 6 bays the tab
  column scrolls (arrows or mouse wheel).
* **Breaking a hub drops ONE hub item that carries everything**: every installed generator (with the items inside
  it, its switches and timer) and the expansions. Nothing falls out separately. Placing that hub item restores it all.

## Duplicator
* Recipe: 4 diamond blocks (corners), 4 obsidian (edges), 1 nether star (centre).
* Put the item you want copied in the gold-framed slot. One copy every 5 seconds (`duplicator_speed`).
* **Stop/Start** pauses it, **Pick up** gives you the Duplicator with its items inside (nothing drops).
* Items that normally can't stack (totems, buckets, tools...) stack up to 64 per output slot here. Shift-clicking
  such a stack moves one item per free inventory slot.

## Enchanter
* Recipe: 4 diamond blocks (corners), 4 bookshelves (edges), 1 enchanting table (centre).
* Put an item (or a book) in the left slot, pick enchantments and levels (up to 10) from the list, put diamonds or
  netherite ingots in the payment slot and press Enchant. Prices depend on how useful the enchantment is.
  1 netherite ingot = 6 diamonds. Enchantments that only have one level (Mending, Silk Touch...) stay at level 1.

## Black GUI (optional resource pack)
`Material_Generators_Black_GUI.zip` (built to `dist/`) re-skins all three GUIs in black. Put it in your
`resourcepacks` folder and enable it. Without it the GUIs use the normal light look.

## Build (easiest: no installs, GitHub does it)
1. Create a free GitHub account and a new empty repository.
2. Upload the *contents* of this folder (about 15 files; make sure the `.github` folder is included) and commit.
3. Open the **Actions** tab -> "Build mod jar" -> wait ~3-5 minutes -> download the `material_generators-jar` artifact.
4. Unzip it and put `material_generators-1.0.0.jar` in your `mods/` folder (Forge 1.20.1, 47.x).
   If you want zinc, install Create 0.5.1 as well.

## Build locally instead
0. Generate the resources first: `pip install pillow` then `python3 tools/gen_resources.py` (GitHub does this step automatically).
Requires JDK 17.

1. Get a Gradle wrapper: copy `gradlew`, `gradlew.bat` and the `gradle/` folder from the official Forge 1.20.1 MDK
   (https://files.minecraftforge.net/ -> 1.20.1 -> "Mdk"), or run `gradle wrapper --gradle-version 8.1.1` if you have Gradle installed.
2. `./gradlew build`
3. The mod jar is in `build/libs/material_generators-1.0.0.jar` and the pack in `dist/`. Drop it in `mods/`.

For testing: `./gradlew runClient`. You can also import the folder into IntelliJ / Eclipse as a Gradle project.

## Changing things
* Add / remove a material: edit `GeneratorDef.java` **and** the `GENERATORS` list in `tools/gen_resources.py`,
  then run `python3 tools/gen_resources.py` (regenerates models, textures, recipes, loot tables, language file).
* Block textures are 32x32, drawn by `tools/pixel_art.py` (side / top / bottom per block). Replace any PNG in `src/main/resources/assets/material_generators/textures/block/`.
