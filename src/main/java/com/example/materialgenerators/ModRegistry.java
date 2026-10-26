package com.example.materialgenerators;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MaterialGenerators.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MaterialGenerators.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MaterialGenerators.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MaterialGenerators.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MaterialGenerators.MOD_ID);

    /** Crafting ingredient for every generator. */
    public static final RegistryObject<Block> GENERATOR_CORE = BLOCKS.register("generator_core",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)));
    public static final RegistryObject<Item> GENERATOR_CORE_ITEM = ITEMS.register("generator_core",
            () -> new BlockItem(GENERATOR_CORE.get(), new Item.Properties()));

    public static final List<RegistryObject<Block>> GENERATOR_BLOCKS = new ArrayList<>();

    static {
        for (GeneratorDef def : GeneratorDef.ALL) {
            for (int tier = 1; tier <= GeneratorDef.TIERS; tier++) {
                final int t = tier;
                String name = def.blockName(tier);
                RegistryObject<Block> block = BLOCKS.register(name, () -> new GeneratorBlock(def, t));
                ITEMS.register(name, () -> new StoredContentsBlockItem(block.get(), new Item.Properties().stacksTo(1)));
                GENERATOR_BLOCKS.add(block);
            }
        }
    }

    public static final RegistryObject<BlockEntityType<GeneratorBlockEntity>> GENERATOR_BE =
            BLOCK_ENTITIES.register("generator", () -> BlockEntityType.Builder
                    .of(GeneratorBlockEntity::new,
                            GENERATOR_BLOCKS.stream().map(RegistryObject::get).toArray(Block[]::new))
                    .build(null));

    // ---- Duplicator ----
    public static final RegistryObject<Block> DUPLICATOR = BLOCKS.register("duplicator", DuplicatorBlock::new);
    public static final RegistryObject<Item> DUPLICATOR_ITEM = ITEMS.register("duplicator",
            () -> new StoredContentsBlockItem(DUPLICATOR.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<DuplicatorBlockEntity>> DUPLICATOR_BE =
            BLOCK_ENTITIES.register("duplicator", () -> BlockEntityType.Builder
                    .of(DuplicatorBlockEntity::new, DUPLICATOR.get())
                    .build(null));
    public static final RegistryObject<MenuType<DuplicatorMenu>> DUPLICATOR_MENU =
            MENUS.register("duplicator", () -> IForgeMenuType.create((id, inv, buf) -> new DuplicatorMenu(id, inv)));

    public static final RegistryObject<MenuType<GeneratorMenu>> GENERATOR_MENU =
            MENUS.register("generator", () -> IForgeMenuType.create((id, inv, buf) -> new GeneratorMenu(id, inv)));

    // ---- Enchanter ----
    public static final RegistryObject<Block> ENCHANTER = BLOCKS.register("enchanter", EnchanterBlock::new);
    public static final RegistryObject<Item> ENCHANTER_ITEM = ITEMS.register("enchanter",
            () -> new StoredContentsBlockItem(ENCHANTER.get(), new Item.Properties()));
    public static final RegistryObject<MenuType<EnchanterMenu>> ENCHANTER_MENU =
            MENUS.register("enchanter", () -> IForgeMenuType.create((id, inv, buf) -> new EnchanterMenu(id, inv)));

    // ---- Generator Hub ----
    public static final RegistryObject<Block> HUB = BLOCKS.register("generator_hub", GeneratorHubBlock::new);
    public static final RegistryObject<Item> HUB_ITEM = ITEMS.register("generator_hub",
            () -> new StoredContentsBlockItem(HUB.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<GeneratorHubBlockEntity>> HUB_BE =
            BLOCK_ENTITIES.register("generator_hub", () -> BlockEntityType.Builder
                    .of(GeneratorHubBlockEntity::new, HUB.get())
                    .build(null));
    public static final RegistryObject<MenuType<GeneratorHubMenu>> HUB_MENU =
            MENUS.register("generator_hub", () -> IForgeMenuType.create((id, inv, buf) -> new GeneratorHubMenu(id, inv)));

    public static final RegistryObject<Item> HUB_EXPANSION = ITEMS.register("hub_expansion",
            () -> new HubExpansionItem(new Item.Properties()));

    /** Position of a generator block in GENERATOR_BLOCKS (-1 if it is not one). */
    public static int generatorIndex(Block block) {
        for (int i = 0; i < GENERATOR_BLOCKS.size(); i++) {
            if (GENERATOR_BLOCKS.get(i).get() == block) {
                return i;
            }
        }
        return -1;
    }

    /** The generator block at that position, or null. */
    public static GeneratorBlock generatorAt(int index) {
        if (index < 0 || index >= GENERATOR_BLOCKS.size()) {
            return null;
        }
        return (GeneratorBlock) GENERATOR_BLOCKS.get(index).get();
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MaterialGenerators.MOD_ID))
                    .icon(() -> new ItemStack(GENERATOR_CORE_ITEM.get()))
                    .displayItems((params, output) ->
                            ITEMS.getEntries().forEach(item -> output.accept(item.get())))
                    .build());

    private ModRegistry() {}
}
