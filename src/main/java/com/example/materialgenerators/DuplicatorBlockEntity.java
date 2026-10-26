package com.example.materialgenerators;

import java.util.Objects;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

/**
 * Slot 0 = the item to copy (it is never used up). Slots 1-9 = copies.
 * Every N ticks (config: duplicator_speed) one copy is added to the output slots.
 * Items that normally do not stack (totems, buckets, tools...) stack up to UNSTACKABLE_LIMIT here.
 * Automation can only take items out of the output slots.
 */
public class DuplicatorBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
    public static final int SIZE = 10;
    /** How many of an unstackable item one output slot holds. */
    public static final int UNSTACKABLE_LIMIT = 64;
    private static final int[] OUTPUT_SLOTS = IntStream.rangeClosed(1, 9).toArray();

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int progress = 0;
    private boolean enabled = true;

    /** Synced to the GUI: [0] = progress, [1] = ticks needed, [2] = running (1) / stopped (0). */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            switch (index) {
                case 0:
                    return progress;
                case 1:
                    return GeneratorConfig.duplicatorInterval();
                default:
                    return enabled ? 1 : 0;
            }
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                progress = value;
            } else if (index == 2) {
                enabled = value != 0;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());

    public DuplicatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.DUPLICATOR_BE.get(), pos, state);
    }

    /** The numbers the GUI reads (also used for GUIs opened from the item). */
    public ContainerData menuData() {
        return data;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a.is(b.getItem()) && Objects.equals(a.getTag(), b.getTag());
    }

    /** Items with a max stack of 1 are allowed to stack here. */
    private static int limitFor(ItemStack stack) {
        return stack.getMaxStackSize() == 1 ? UNSTACKABLE_LIMIT : stack.getMaxStackSize();
    }

    private boolean hasSpaceFor(ItemStack template) {
        for (int i = 1; i < SIZE; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty() || (same(stack, template) && stack.getCount() < limitFor(stack))) {
                return true;
            }
        }
        return false;
    }

    private void addCopy(ItemStack template) {
        for (int i = 1; i < SIZE; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                ItemStack copy = template.copy();
                copy.setCount(1);
                items.set(i, copy);
                return;
            }
            if (same(stack, template) && stack.getCount() < limitFor(stack)) {
                stack.grow(1);
                return;
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DuplicatorBlockEntity be) {
        if (!be.enabled) {
            return;
        }
        ItemStack template = be.items.get(0);
        if (template.isEmpty()) {
            be.progress = 0;
            return;
        }
        if (!be.hasSpaceFor(template)) {
            return; // outputs full: pause
        }
        be.progress++;
        if (be.progress < GeneratorConfig.duplicatorInterval()) {
            return;
        }
        be.progress = 0;
        be.addCopy(template);
        be.setChanged();
    }

    public void toggleEnabled() {
        enabled = !enabled;
        setChanged();
    }

    /** Removes the block and gives the player the Duplicator with its items stored inside (nothing drops). */
    public void pickUp(ServerPlayer player) {
        if (level == null || level.isClientSide) {
            return;
        }
        ItemStack stack = new ItemStack(getBlockState().getBlock().asItem());
        BlockItem.setBlockEntityData(stack, getType(), saveWithoutMetadata());
        for (int i = 0; i < SIZE; i++) {
            items.set(i, ItemStack.EMPTY);
        }
        level.removeBlock(getBlockPos(), false);
        player.getInventory().placeItemBackInInventory(stack);
        player.closeContainer();
    }

    // ---- container plumbing ----

    @Override
    protected Component getDefaultName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new DuplicatorMenu(id, inventory, this, data);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
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
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, items);
        }
        progress = tag.getInt("Progress");
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items);
        }
        tag.putInt("Progress", progress);
        tag.putBoolean("Enabled", enabled);
    }

    // ---- automation: take copies out only ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return OUTPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= 1;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!remove && side != null && cap == ForgeCapabilities.ITEM_HANDLER) {
            return handlers[side.ordinal()].cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        for (LazyOptional<? extends IItemHandler> handler : handlers) {
            handler.invalidate();
        }
    }
}
