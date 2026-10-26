package com.example.materialgenerators;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Slots 0-53 = generated items (take only), 54-80 = player inventory, 81-89 = hotbar.
 * Button ids (sent from the screen): 0 = switch on/off, 1 = pick the generator up with its items inside,
 * 2 = switch between making items and making blocks, 3 = switch to the next material.
 *
 * Opened from an item (sneak + right-click), "sourceStack" is that item: the menu works on a temporary copy of
 * the generator's data, produces while open, and writes everything back into the item when it closes.
 */
public class GeneratorMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE = 0;
    public static final int BUTTON_PICK_UP = 1;
    public static final int BUTTON_MODE = 2;
    public static final int BUTTON_VARIANT = 3;
    private static final int GEN_SLOTS = GeneratorBlockEntity.SIZE;

    private final Container container;
    private final ContainerData data;
    @Nullable
    private final GeneratorBlockEntity blockEntity;
    @Nullable
    private final ItemStack sourceStack;
    private final Player owner;
    private final DataSlot itemMode = DataSlot.standalone();
    private int saveCounter = 0;

    /** Client side. */
    public GeneratorMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(GEN_SLOTS), new SimpleContainerData(7), null);
    }

    /** Server side, for a placed generator block. */
    public GeneratorMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        this(id, playerInventory, container, data, null);
    }

    /** Server side. sourceStack != null means the GUI was opened from that generator item. */
    public GeneratorMenu(int id, Inventory playerInventory, Container container, ContainerData data,
                         @Nullable ItemStack sourceStack) {
        super(ModRegistry.GENERATOR_MENU.get(), id);
        checkContainerSize(container, GEN_SLOTS);
        checkContainerDataCount(data, 7);
        this.container = container;
        this.data = data;
        this.blockEntity = container instanceof GeneratorBlockEntity be ? be : null;
        this.sourceStack = sourceStack;
        this.owner = playerInventory.player;
        container.startOpen(playerInventory.player);
        if (sourceStack != null) {
            HeldGenerator.OPEN.add(sourceStack);
            itemMode.set(1);
        }

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new OutputSlot(container, col + row * 9, 8 + col * 18, 46 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new CarriedGui.LockableSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, 168 + row * 18, sourceStack));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new CarriedGui.LockableSlot(playerInventory, col, 8 + col * 18, 226, sourceStack));
        }
        addDataSlots(data);
        addDataSlot(itemMode);
    }

    /** True when this GUI belongs to a generator item rather than a placed block (no "pick up" then). */
    public boolean isItemMode() {
        return itemMode.get() != 0;
    }

    public boolean isEnabled() {
        return data.get(0) != 0;
    }

    public boolean isBlockMode() {
        return data.get(3) != 0;
    }

    public boolean hasBlockForm() {
        return data.get(4) != 0;
    }

    /** Which material of the generator is selected. */
    public int getVariantIndex() {
        return data.get(5);
    }

    /** Index of this generator in GeneratorDef.ALL. */
    public int getDefIndex() {
        return data.get(6);
    }

    public int getProgress() {
        return data.get(1);
    }

    public int getInterval() {
        return data.get(2);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null) {
            return false;
        }
        if (id == BUTTON_TOGGLE) {
            blockEntity.toggleEnabled();
            return true;
        }
        if (id == BUTTON_VARIANT) {
            blockEntity.cycleVariant();
            return true;
        }
        if (id == BUTTON_MODE) {
            blockEntity.toggleBlockMode();
            return true;
        }
        if (id == BUTTON_PICK_UP && sourceStack == null && player instanceof ServerPlayer serverPlayer) {
            blockEntity.pickUp(serverPlayer);
            return true;
        }
        return false;
    }

    /** While opened from an item: keep the generator running and save it into the item now and then. */
    @Override
    public void broadcastChanges() {
        if (sourceStack != null && blockEntity != null && !owner.level().isClientSide) {
            GeneratorBlockEntity.serverTick(owner.level(), BlockPos.ZERO, blockEntity.getBlockState(), blockEntity);
            if (++saveCounter % 20 == 0) {
                saveToItem();
            }
        }
        super.broadcastChanges();
    }

    private void saveToItem() {
        if (sourceStack != null && blockEntity != null) {
            BlockItem.setBlockEntityData(sourceStack, blockEntity.getType(), blockEntity.saveWithoutMetadata());
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem() && index < GEN_SLOTS) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (!moveItemStackTo(stack, GEN_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            slot.onTake(player, stack);
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (sourceStack != null) {
            return CarriedGui.isCarrying(player, sourceStack);
        }
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
        if (sourceStack != null) {
            if (!player.level().isClientSide) {
                saveToItem();
            }
            HeldGenerator.OPEN.remove(sourceStack);
        }
    }

    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
