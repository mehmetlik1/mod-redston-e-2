package com.example.materialgenerators;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

/**
 * 54-slot inventory that receives one item every N ticks (while switched on).
 * Hoppers, pipes and funnels can take items out but cannot put items in.
 */
public class GeneratorBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
    public static final int SIZE = 54;
    private static final int[] ALL_SLOTS = IntStream.range(0, SIZE).toArray();

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int progress = 0;
    private boolean enabled = true;
    private boolean blockMode = false;
    private int variant = 0;

    /**
     * Synced to the GUI: [0] on/off, [1] progress, [2] ticks per item, [3] making blocks, [4] has a block form,
     * [5] selected variant, [6] index of this generator in GeneratorDef.ALL.
     */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            int interval = currentInterval();
            int scale = Math.max(1, (interval + 31999) / 32000); // values travel as 16-bit numbers
            switch (index) {
                case 0:
                    return enabled ? 1 : 0;
                case 1:
                    return Math.min(progress / scale, 32000);
                case 2:
                    return Math.max(1, interval / scale);
                case 3:
                    return makesBlocks() ? 1 : 0;
                case 4:
                    return hasBlockForm() ? 1 : 0;
                case 5:
                    return currentDef() == null ? 0 : Math.floorMod(variant, currentDef().variants().size());
                default:
                    return currentDef() == null ? 0 : GeneratorDef.ALL.indexOf(currentDef());
            }
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                enabled = value != 0;
            }
        }

        @Override
        public int getCount() {
            return 7;
        }
    };

    private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GENERATOR_BE.get(), pos, state);
    }

    /** The numbers the GUI reads (also used for GUIs opened from the item). */
    public ContainerData menuData() {
        return data;
    }

    @Nullable
    private GeneratorDef currentDef() {
        return getBlockState().getBlock() instanceof GeneratorBlock block ? block.def() : null;
    }

    @Nullable
    private GeneratorDef.Variant currentVariant() {
        GeneratorDef def = currentDef();
        return def == null ? null : def.variant(variant);
    }

    private boolean hasBlockForm() {
        GeneratorDef.Variant v = currentVariant();
        return v != null && v.hasBlock();
    }

    private boolean makesBlocks() {
        return blockMode && hasBlockForm();
    }

    /** Ticks per produced item or block, including the block-mode multiplier. */
    private int currentInterval() {
        if (getBlockState().getBlock() instanceof GeneratorBlock block) {
            int base = GeneratorConfig.interval(block.def(), block.tier());
            return makesBlocks() ? base * currentVariant().blockMultiplier() : base;
        }
        return 20;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity be) {
        if (!be.enabled || !(state.getBlock() instanceof GeneratorBlock block)) {
            return;
        }
        GeneratorDef.Variant made = block.def().variant(be.variant);
        boolean blocks = be.makesBlocks();
        int interval = be.currentInterval();
        if (be.progress < interval) {
            be.progress++;
        }
        if (be.progress < interval) {
            return;
        }
        Item output = blocks ? made.resolveBlock() : made.resolveOutput();
        if (output == Items.AIR) {
            return; // e.g. Create not installed
        }
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = be.items.get(i);
            if (stack.isEmpty()) {
                be.items.set(i, new ItemStack(output));
            } else if (stack.is(output) && !stack.hasTag() && stack.getCount() < stack.getMaxStackSize()) {
                stack.grow(1);
            } else {
                continue;
            }
            be.progress = 0;
            be.setChanged();
            return;
        }
        // Inventory full: wait (progress stays at the interval) until space opens up.
    }

    /** Switches the generator on or off. */
    public void toggleEnabled() {
        enabled = !enabled;
        setChanged();
    }

    /** Switches between making items and making blocks (restarts the timer so it cannot be abused). */
    public void toggleBlockMode() {
        if (!hasBlockForm()) {
            return;
        }
        blockMode = !blockMode;
        progress = 0;
        setChanged();
    }

    /** Switches to the next material of this generator (e.g. Stone -> Cobblestone -> Deepslate). */
    public void cycleVariant() {
        GeneratorDef def = currentDef();
        if (def == null || def.variants().size() <= 1) {
            return;
        }
        variant = (Math.floorMod(variant, def.variants().size()) + 1) % def.variants().size();
        progress = 0;
        setChanged();
    }

    /**
     * Removes this block and hands the player the generator item with its inventory stored inside it
     * (like a shulker box), so nothing is dropped on the ground. Placing it again restores everything.
     */
    public void pickUp(ServerPlayer player) {
        if (level == null || level.isClientSide) {
            return;
        }
        ItemStack stack = new ItemStack(getBlockState().getBlock().asItem());
        BlockItem.setBlockEntityData(stack, getType(), saveWithoutMetadata());
        for (int i = 0; i < SIZE; i++) {
            items.set(i, ItemStack.EMPTY); // so breaking the block drops nothing
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
        return new GeneratorMenu(id, inventory, this, data);
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
        blockMode = tag.getBoolean("BlockMode");
        variant = tag.getInt("Variant");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items);
        }
        tag.putInt("Progress", progress);
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("BlockMode", blockMode);
        tag.putInt("Variant", variant);
    }

    // ---- automation: extract only ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
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
