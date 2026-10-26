package com.example.materialgenerators;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * One kind of generator. "id" is used for block names, config keys and textures:
 *   block ids:   {id}_generator   and   {id}_generator_tier_ii ... _tier_v
 *   config keys: {id}_speed       and   {id}_tier2_speed ... _tier5_speed
 *
 * A generator can offer several materials ("variants", e.g. Stone / Cobblestone / Deepslate) that the
 * player switches between in the GUI. Each variant has an item, optionally a block form (made in BLOCK
 * mode) and a multiplier saying how many times longer one block takes than one item.
 *
 * To add a new material, add a line to ALL, add it to tools/gen_resources.py and re-run that script.
 */
public record GeneratorDef(String id, String displayName, List<Variant> variants) {

    public record Variant(ResourceLocation output, @Nullable ResourceLocation block, int blockMultiplier) {
        public boolean hasBlock() {
            return block != null;
        }

        /** The item this variant makes, or AIR if its mod (Create) is not installed. */
        public Item resolveOutput() {
            Item item = ForgeRegistries.ITEMS.getValue(output);
            return item == null ? Items.AIR : item;
        }

        /** The block item made in block mode, or AIR if there is none / its mod is missing. */
        public Item resolveBlock() {
            if (block == null) {
                return Items.AIR;
            }
            Item item = ForgeRegistries.ITEMS.getValue(block);
            return item == null ? Items.AIR : item;
        }
    }

    private static Variant v(String namespace, String path) {
        return new Variant(new ResourceLocation(namespace, path), null, 1);
    }

    private static Variant v(String namespace, String path, String blockNamespace, String blockPath, int multiplier) {
        return new Variant(new ResourceLocation(namespace, path), new ResourceLocation(blockNamespace, blockPath), multiplier);
    }

    private static GeneratorDef of(String id, String displayName, Variant... variants) {
        return new GeneratorDef(id, displayName, List.of(variants));
    }

    public static final List<GeneratorDef> ALL = List.of(
            // Create (optional dependency, looked up by registry name at runtime)
            of("zinc", "Zinc", v("create", "zinc_ingot", "create", "zinc_block", 9)),
            of("andesite_alloy", "Andesite Alloy", v("create", "andesite_alloy", "create", "andesite_alloy_block", 9)),
            // Misc
            of("blaze_rod", "Blaze Rod", v("minecraft", "blaze_rod")),
            of("gunpowder", "Gunpowder", v("minecraft", "gunpowder")),
            of("nether_star", "Nether Star", v("minecraft", "nether_star")),
            of("quartz", "Quartz", v("minecraft", "quartz", "minecraft", "quartz_block", 4)),
            // Ores, gems and stone
            of("copper", "Copper", v("minecraft", "copper_ingot", "minecraft", "copper_block", 9)),
            of("iron", "Iron", v("minecraft", "iron_ingot", "minecraft", "iron_block", 9)),
            of("gold", "Gold", v("minecraft", "gold_ingot", "minecraft", "gold_block", 9)),
            of("diamond", "Diamond", v("minecraft", "diamond", "minecraft", "diamond_block", 9)),
            of("emerald", "Emerald", v("minecraft", "emerald", "minecraft", "emerald_block", 9)),
            of("netherite", "Netherite", v("minecraft", "netherite_ingot", "minecraft", "netherite_block", 9)),
            of("lapis", "Lapis", v("minecraft", "lapis_lazuli", "minecraft", "lapis_block", 9)),
            of("redstone", "Redstone", v("minecraft", "redstone", "minecraft", "redstone_block", 9)),
            of("coal", "Coal", v("minecraft", "coal", "minecraft", "coal_block", 9)),
            of("glowstone", "Glowstone", v("minecraft", "glowstone_dust", "minecraft", "glowstone", 4)),
            of("amethyst", "Amethyst", v("minecraft", "amethyst_shard", "minecraft", "amethyst_block", 4)),
            of("dripstone", "Dripstone", v("minecraft", "pointed_dripstone", "minecraft", "dripstone_block", 4)),
            // Switchable: Stone / Cobblestone / Deepslate (block mode = bricks / mossy cobblestone / bricks)
            of("stone", "Stone",
                    v("minecraft", "stone", "minecraft", "stone_bricks", 2),
                    v("minecraft", "cobblestone", "minecraft", "mossy_cobblestone", 2),
                    v("minecraft", "deepslate", "minecraft", "deepslate_bricks", 2)),
            // Switchable: Obsidian / Crying Obsidian (no block form)
            of("obsidian", "Obsidian", v("minecraft", "obsidian"), v("minecraft", "crying_obsidian")),
            // Woods (block mode = the all-bark "wood" block)
            of("oak_log", "Oak Log", v("minecraft", "oak_log", "minecraft", "oak_wood", 2)),
            of("spruce_log", "Spruce Log", v("minecraft", "spruce_log", "minecraft", "spruce_wood", 2)),
            of("birch_log", "Birch Log", v("minecraft", "birch_log", "minecraft", "birch_wood", 2)),
            of("jungle_log", "Jungle Log", v("minecraft", "jungle_log", "minecraft", "jungle_wood", 2)),
            of("acacia_log", "Acacia Log", v("minecraft", "acacia_log", "minecraft", "acacia_wood", 2)),
            of("dark_oak_log", "Dark Oak Log", v("minecraft", "dark_oak_log", "minecraft", "dark_oak_wood", 2)),
            of("mangrove_log", "Mangrove Log", v("minecraft", "mangrove_log", "minecraft", "mangrove_wood", 2)),
            of("cherry_log", "Cherry Log", v("minecraft", "cherry_log", "minecraft", "cherry_wood", 2)),
            of("crimson_stem", "Crimson Stem", v("minecraft", "crimson_stem", "minecraft", "crimson_hyphae", 2)),
            of("warped_stem", "Warped Stem", v("minecraft", "warped_stem", "minecraft", "warped_hyphae", 2))
    );

    public static final int TIERS = 5;
    private static final String[] TIER_SUFFIX = {"_generator", "_generator_tier_ii", "_generator_tier_iii",
            "_generator_tier_iv", "_generator_tier_v"};

    /** Variant number wraps around, so any saved number is safe. */
    public Variant variant(int index) {
        return variants.get(Math.floorMod(index, variants.size()));
    }

    public String blockName(int tier) {
        return id + TIER_SUFFIX[tier - 1];
    }
}
