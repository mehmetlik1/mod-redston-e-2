package com.example.materialgenerators;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MaterialGenerators.MOD_ID)
public class MaterialGenerators {
    public static final String MOD_ID = "material_generators";

    public MaterialGenerators() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.BLOCKS.register(bus);
        ModRegistry.ITEMS.register(bus);
        ModRegistry.BLOCK_ENTITIES.register(bus);
        ModRegistry.MENUS.register(bus);
        ModRegistry.TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GeneratorConfig.SPEC, "material_generators.toml");
    }
}
