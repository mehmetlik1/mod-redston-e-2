package com.example.materialgenerators;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Prices are in diamonds. A netherite ingot counts as NETHERITE_VALUE diamonds.
 * The more generally useful an enchantment is, the more it costs.
 */
public final class EnchantPricing {
    public static final int LEVEL_CAP = 10;
    public static final int NETHERITE_VALUE = 6;

    private record Price(double value, boolean flat) {}

    private static final Map<String, Price> PRICES = new HashMap<>();

    private static void perLevel(String id, double v) {
        PRICES.put("minecraft:" + id, new Price(v, false));
    }

    private static void flat(String id, double v) {
        PRICES.put("minecraft:" + id, new Price(v, true));
    }

    static {
        // top tier: useful on almost everything you do
        flat("mending", 24);
        flat("silk_touch", 12);
        flat("infinity", 14);
        perLevel("efficiency", 3);
        perLevel("fortune", 3);
        perLevel("sharpness", 3);
        perLevel("protection", 3);
        perLevel("looting", 3);
        perLevel("power", 3);
        perLevel("unbreaking", 2);
        // solid
        perLevel("sweeping_edge", 2);
        perLevel("fire_aspect", 2);
        perLevel("feather_falling", 2);
        perLevel("depth_strider", 2);
        perLevel("frost_walker", 2);
        perLevel("soul_speed", 2);
        perLevel("swift_sneak", 2);
        perLevel("quick_charge", 2);
        perLevel("riptide", 2);
        flat("multishot", 4);
        flat("flame", 4);
        flat("channeling", 4);
        flat("aqua_affinity", 3);
        // situational
        perLevel("respiration", 1.5);
        perLevel("impaling", 1.5);
        perLevel("piercing", 1.5);
        perLevel("luck_of_the_sea", 1.5);
        perLevel("lure", 1.5);
        perLevel("knockback", 1);
        perLevel("punch", 1);
        perLevel("smite", 1);
        perLevel("bane_of_arthropods", 1);
        perLevel("fire_protection", 1);
        perLevel("blast_protection", 1);
        perLevel("projectile_protection", 1);
        perLevel("thorns", 1);
        perLevel("loyalty", 1);
        // curses
        flat("binding_curse", 1);
        flat("vanishing_curse", 1);
    }

    private EnchantPricing() {}

    /** Enchantments with only one level (Mending, Silk Touch...) gain nothing from higher levels. */
    public static int maxLevel(Enchantment enchantment) {
        return enchantment.getMaxLevel() <= 1 ? 1 : LEVEL_CAP;
    }

    public static int price(Enchantment enchantment, int level) {
        if (level <= 0) {
            return 0;
        }
        ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        Price price = key == null ? null : PRICES.get(key.toString());
        if (price == null) {
            price = enchantment.getMaxLevel() <= 1 ? new Price(6, true) : new Price(2, false);
        }
        if (price.flat()) {
            return (int) Math.ceil(price.value());
        }
        return (int) Math.ceil(price.value() * level * (1 + 0.06 * (level - 1)));
    }

    public static int netheriteNeeded(int diamonds) {
        return (diamonds + NETHERITE_VALUE - 1) / NETHERITE_VALUE;
    }
}
