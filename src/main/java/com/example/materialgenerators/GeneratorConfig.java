package com.example.materialgenerators;

import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.common.ForgeConfigSpec;

/** Ticks between produced items. Edit config/material_generators.toml (20 ticks = 1 second). */
public final class GeneratorConfig {
    public static final ForgeConfigSpec SPEC;
    /** Default ticks per item for tiers I..V. */
    private static final int[] DEFAULTS = {20, 10, 5, 2, 1};
    private static final Map<String, ForgeConfigSpec.IntValue> SPEEDS = new HashMap<>();
    private static final ForgeConfigSpec.IntValue DUPLICATOR_SPEED;
    private static final ForgeConfigSpec.BooleanValue WORK_ANYWHERE_IN_INVENTORY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Ticks between each produced item (20 ticks = 1 second).").push("speeds");
        for (GeneratorDef d : GeneratorDef.ALL) {
            for (int tier = 1; tier <= GeneratorDef.TIERS; tier++) {
                String key = tier == 1 ? d.id() + "_speed" : d.id() + "_tier" + tier + "_speed";
                SPEEDS.put(d.id() + ":" + tier, b.defineInRange(key, DEFAULTS[tier - 1], 1, 1_000_000));
            }
        }
        b.pop();
        b.comment("Duplicator: ticks needed to make one copy (20 ticks = 1 second). Max 32000.").push("duplicator");
        DUPLICATOR_SPEED = b.defineInRange("duplicator_speed", 100, 1, 32000);
        b.pop();
        b.comment("Generator items keep producing while carried (if switched on).").push("carried");
        WORK_ANYWHERE_IN_INVENTORY = b.comment("false = only while held in your main hand or offhand; true = anywhere in your inventory")
                .define("work_anywhere_in_inventory", false);
        b.pop();
        SPEC = b.build();
    }

    private GeneratorConfig() {}

    public static int interval(GeneratorDef def, int tier) {
        try {
            return SPEEDS.get(def.id() + ":" + tier).get();
        } catch (IllegalStateException notLoadedYet) {
            return DEFAULTS[tier - 1];
        }
    }

    public static boolean workAnywhereInInventory() {
        try {
            return WORK_ANYWHERE_IN_INVENTORY.get();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        }
    }

    public static int duplicatorInterval() {
        try {
            return DUPLICATOR_SPEED.get();
        } catch (IllegalStateException notLoadedYet) {
            return 100;
        }
    }
}
