package com.example.materialgenerators;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkHooks;

/**
 * Opens the GUI of a generator, hub, Duplicator or Enchanter straight from the item (sneak + right-click),
 * without placing it. The GUI runs on a temporary block entity loaded from the item's data; the menu saves it
 * back into the item. (The Enchanter has no data to keep.)
 */
public final class ItemMenus {
    private ItemMenus() {}

    public static void open(ServerPlayer player, ItemStack stack, Block block) {
        CompoundTag saved = stack.getTagElement("BlockEntityTag");
        if (block instanceof GeneratorBlock generator) {
            GeneratorBlockEntity be = new GeneratorBlockEntity(BlockPos.ZERO, generator.defaultBlockState());
            if (saved != null) {
                be.load(saved.copy());
            }
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (id, inventory, p) -> new GeneratorMenu(id, inventory, be, be.menuData(), stack),
                    stack.getHoverName()));
        } else if (block instanceof DuplicatorBlock) {
            DuplicatorBlockEntity be = new DuplicatorBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            if (saved != null) {
                be.load(saved.copy());
            }
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (id, inventory, p) -> new DuplicatorMenu(id, inventory, be, be.menuData(), stack),
                    stack.getHoverName()));
        } else if (block instanceof EnchanterBlock) {
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (id, inventory, p) -> new EnchanterMenu(id, inventory, stack),
                    stack.getHoverName()));
        } else if (block instanceof GeneratorHubBlock) {
            GeneratorHubBlockEntity hub = new GeneratorHubBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            if (saved != null) {
                hub.load(saved.copy());
            }
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (id, inventory, p) -> new GeneratorHubMenu(id, inventory, hub, stack),
                    stack.getHoverName()));
        }
    }
}
