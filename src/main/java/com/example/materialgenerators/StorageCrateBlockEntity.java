package com.example.materialgenerators;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

/**
 * A chest-like crate that grows: it starts with BASE_ROWS rows of 9 slots and every Crate Upgrade adds one row,
 * up to MAX_ROWS. It uses the normal chest GUI, so it works with hoppers, pipes and funnels like a chest.
 */
public class StorageCrateBlockEntity extends RandomizableContainerBlockEntity {
    public static final int COLUMNS = 9;
    public static final int BASE_ROWS = 2;
    public static final int MAX_ROWS = 6;

    private NonNullList<ItemStack> items = NonNullList.withSize(MAX_ROWS * COLUMNS, ItemStack.EMPTY);
    private int rows = BASE_ROWS;
    private final LazyOptional<IItemHandler> handler = LazyOptional.of(() -> new InvWrapper(this));

    public StorageCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CRATE_BE.get(), pos, state);
    }

    public int getRows() {
        return rows;
    }

    /** Adds one row. Returns false if the crate is already fully upgraded. */
    public boolean upgrade() {
        if (rows >= MAX_ROWS) {
            return false;
        }
        rows++;
        setChanged();
        return true;
    }

    private static MenuType<ChestMenu> menuTypeFor(int rows) {
        switch (rows) {
            case 1:
                return MenuType.GENERIC_9x1;
            case 2:
                return MenuType.GENERIC_9x2;
            case 3:
                return MenuType.GENERIC_9x3;
            case 4:
                return MenuType.GENERIC_9x4;
            case 5:
                return MenuType.GENERIC_9x5;
            default:
                return MenuType.GENERIC_9x6;
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId())
                .append(Component.literal(" (" + getContainerSize() + ")"));
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ChestMenu(menuTypeFor(rows), id, inventory, this, rows);
    }

    @Override
    public int getContainerSize() {
        return rows * COLUMNS;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> newItems) {
        items = newItems;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        rows = Math.max(BASE_ROWS, Math.min(MAX_ROWS, tag.contains("Rows") ? tag.getInt("Rows") : BASE_ROWS));
        items = NonNullList.withSize(MAX_ROWS * COLUMNS, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, items);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Rows", rows);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items);
        }
    }

    // ---- pipes / funnels / hoppers ----

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!remove && cap == ForgeCapabilities.ITEM_HANDLER) {
            return handler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handler.invalidate();
    }
}
