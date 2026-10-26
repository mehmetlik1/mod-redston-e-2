package com.example.materialgenerators;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A block with generator bays (6 to start, upgradeable to 18 with Hub Expansions). Each bay holds one generator item and keeps that generator's own pocket of
 * items (POCKET slots), switches (on/off, items/blocks, material) and timer. All bays run at the same time.
 * Putting a generator in loads the items stored in it into the pocket; taking it out packs them back inside it.
 */
public class GeneratorHubBlockEntity extends BlockEntity implements MenuProvider {
    /** A new hub has BASE_BAYS bays; every Hub Expansion adds KIT_BAYS more, up to MAX_BAYS. */
    public static final int BASE_BAYS = 6;
    public static final int KIT_BAYS = 2;
    public static final int MAX_BAYS = 18;
    public static final int POCKET = 54;

    /** One generator installed in the hub. */
    public static final class Bay {
        @Nullable
        public GeneratorBlock block = null;
        public int genIndex = -1;
        public final NonNullList<ItemStack> items = NonNullList.withSize(POCKET, ItemStack.EMPTY);
        public int progress = 0;
        public boolean enabled = true;
        public boolean blockMode = false;
        public int variant = 0;

        public boolean isEmpty() {
            return block == null;
        }

        private GeneratorDef.Variant currentVariant() {
            return block.def().variant(variant);
        }

        public boolean hasBlockForm() {
            return block != null && currentVariant().hasBlock();
        }

        public boolean makesBlocks() {
            return blockMode && hasBlockForm();
        }

        /** Ticks per produced item or block, including the block-mode multiplier. */
        public int interval() {
            if (block == null) {
                return 20;
            }
            int base = GeneratorConfig.interval(block.def(), block.tier());
            return makesBlocks() ? base * currentVariant().blockMultiplier() : base;
        }

        /** Same logic as a single generator block. Returns true if an item was made. */
        boolean tick() {
            if (block == null || !enabled) {
                return false;
            }
            GeneratorDef.Variant made = currentVariant();
            int interval = interval();
            if (progress < interval) {
                progress++;
            }
            if (progress < interval) {
                return false;
            }
            Item output = makesBlocks() ? made.resolveBlock() : made.resolveOutput();
            if (output == Items.AIR) {
                return false;
            }
            for (int i = 0; i < POCKET; i++) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) {
                    items.set(i, new ItemStack(output));
                } else if (stack.is(output) && !stack.hasTag() && stack.getCount() < stack.getMaxStackSize()) {
                    stack.grow(1);
                } else {
                    continue;
                }
                progress = 0;
                return true;
            }
            return false; // pocket full: wait
        }

        public void toggleEnabled() {
            if (block != null) {
                enabled = !enabled;
            }
        }

        public void toggleBlockMode() {
            if (hasBlockForm()) {
                blockMode = !blockMode;
                progress = 0;
            }
        }

        public void cycleVariant() {
            if (block != null && block.def().variants().size() > 1) {
                int count = block.def().variants().size();
                variant = (Math.floorMod(variant, count) + 1) % count;
                progress = 0;
            }
        }

        private void clear() {
            block = null;
            genIndex = -1;
            for (int i = 0; i < POCKET; i++) {
                items.set(i, ItemStack.EMPTY);
            }
            progress = 0;
            enabled = true;
            blockMode = false;
            variant = 0;
        }

        /** Installs a generator item; the items stored inside it become this bay's pocket. */
        public void install(ItemStack stack) {
            if (!isGeneratorItem(stack)) {
                return;
            }
            clear();
            block = (GeneratorBlock) ((BlockItem) stack.getItem()).getBlock();
            genIndex = ModRegistry.generatorIndex(block);
            CompoundTag tag = stack.getTagElement("BlockEntityTag");
            if (tag != null) {
                ContainerHelper.loadAllItems(tag, items);
                progress = tag.getInt("Progress");
                enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
                blockMode = tag.getBoolean("BlockMode");
                variant = tag.getInt("Variant");
            }
        }

        /** Takes the generator out as an item with its pocket and settings stored inside it. */
        public ItemStack extract() {
            if (block == null) {
                return ItemStack.EMPTY;
            }
            ItemStack out = new ItemStack(block.asItem());
            CompoundTag tag = new CompoundTag();
            ContainerHelper.saveAllItems(tag, items);
            tag.putInt("Progress", progress);
            tag.putBoolean("Enabled", enabled);
            tag.putBoolean("BlockMode", blockMode);
            tag.putInt("Variant", variant);
            BlockItem.setBlockEntityData(out, ModRegistry.GENERATOR_BE.get(), tag);
            clear();
            return out;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (block == null) {
                tag.putString("Gen", "");
                return tag;
            }
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
            tag.putString("Gen", key == null ? "" : key.toString());
            ContainerHelper.saveAllItems(tag, items);
            tag.putInt("Progress", progress);
            tag.putBoolean("Enabled", enabled);
            tag.putBoolean("BlockMode", blockMode);
            tag.putInt("Variant", variant);
            return tag;
        }

        void load(CompoundTag tag) {
            clear();
            String id = tag.getString("Gen");
            if (id.isEmpty()) {
                return;
            }
            Block found = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
            if (!(found instanceof GeneratorBlock generator)) {
                return;
            }
            block = generator;
            genIndex = ModRegistry.generatorIndex(generator);
            ContainerHelper.loadAllItems(tag, items);
            progress = tag.getInt("Progress");
            enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
            blockMode = tag.getBoolean("BlockMode");
            variant = tag.getInt("Variant");
        }
    }

    public static boolean isGeneratorItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof GeneratorBlock;
    }

    private final Bay[] bays = new Bay[MAX_BAYS];
    private int bayCount = BASE_BAYS;
    private final LazyOptional<IItemHandler> handler = LazyOptional.of(() -> new HubItemHandler(this));

    public GeneratorHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.HUB_BE.get(), pos, state);
        for (int i = 0; i < MAX_BAYS; i++) {
            bays[i] = new Bay();
        }
    }

    public int getBayCount() {
        return bayCount;
    }

    /** Adds KIT_BAYS bays. Returns false if the hub is already fully expanded. */
    public boolean expand() {
        if (bayCount + KIT_BAYS > MAX_BAYS) {
            return false;
        }
        bayCount += KIT_BAYS;
        setChanged();
        return true;
    }

    /** The bay at that position (clamped to the bays this hub has). */
    public Bay bay(int index) {
        return bays[Math.max(0, Math.min(bayCount - 1, index))];
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorHubBlockEntity be) {
        boolean changed = false;
        for (int i = 0; i < be.bayCount; i++) {
            changed |= be.bays[i].tick();
        }
        if (changed) {
            be.setChanged();
        }
    }

    // ---- menu ----

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new GeneratorHubMenu(id, inventory, this);
    }

    // ---- saving ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        bayCount = tag.contains("BayCount")
                ? Math.max(BASE_BAYS, Math.min(MAX_BAYS, tag.getInt("BayCount"))) : BASE_BAYS;
        ListTag list = tag.getList("Bays", 10);
        for (int i = 0; i < MAX_BAYS; i++) {
            if (i < list.size()) {
                bays[i].load(list.getCompound(i));
            } else {
                bays[i].load(new CompoundTag());
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BayCount", bayCount);
        ListTag list = new ListTag();
        for (int i = 0; i < bayCount; i++) {
            list.add(bays[i].save());
        }
        tag.put("Bays", list);
    }

    // ---- automation: take items out of any bay's pocket, nothing goes in ----

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

    /** Exposes every bay's pocket to hoppers and pipes (extract only). */
    private static final class HubItemHandler implements IItemHandler {
        private final GeneratorHubBlockEntity hub;

        HubItemHandler(GeneratorHubBlockEntity hub) {
            this.hub = hub;
        }

        @Override
        public int getSlots() {
            return hub.bayCount * POCKET;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return hub.bays[slot / POCKET].items.get(slot % POCKET);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            NonNullList<ItemStack> items = hub.bays[slot / POCKET].items;
            ItemStack stack = items.get(slot % POCKET);
            if (stack.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, Math.min(stack.getCount(), stack.getMaxStackSize()));
            ItemStack out = stack.copy();
            out.setCount(taken);
            if (!simulate) {
                if (taken >= stack.getCount()) {
                    items.set(slot % POCKET, ItemStack.EMPTY);
                } else {
                    stack.shrink(taken);
                }
                hub.setChanged();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
