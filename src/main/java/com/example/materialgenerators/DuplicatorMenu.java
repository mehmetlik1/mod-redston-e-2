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
 * Slot layout: 0 = item to copy, 1-9 = copies (take only), 10-36 = player inventory, 37-45 = hotbar.
 * Pixel positions must match textures/gui/duplicator.png (made by tools/gen_resources.py).
 *
 * Opened from the Duplicator item (sneak + right-click), "sourceStack" is that item: the menu works on a temporary
 * copy of its data, copies while open, and writes everything back into the item when it closes.
 */
public class DuplicatorMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE = 0;
    public static final int BUTTON_PICK_UP = 1;

    private final Container container;
    private final ContainerData data;
    @Nullable
    private final DuplicatorBlockEntity blockEntity;
    @Nullable
    private final ItemStack sourceStack;
    private final Player owner;
    private final DataSlot itemMode = DataSlot.standalone();
    private int saveCounter = 0;

    /** Client side. */
    public DuplicatorMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(DuplicatorBlockEntity.SIZE), new SimpleContainerData(3), null);
    }

    /** Server side, for a placed Duplicator block. */
    public DuplicatorMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        this(id, playerInventory, container, data, null);
    }

    /** Server side. sourceStack != null means the GUI was opened from that Duplicator item. */
    public DuplicatorMenu(int id, Inventory playerInventory, Container container, ContainerData data,
                          @Nullable ItemStack sourceStack) {
        super(ModRegistry.DUPLICATOR_MENU.get(), id);
        checkContainerSize(container, DuplicatorBlockEntity.SIZE);
        checkContainerDataCount(data, 3);
        this.container = container;
        this.data = data;
        this.blockEntity = container instanceof DuplicatorBlockEntity be ? be : null;
        this.sourceStack = sourceStack;
        this.owner = playerInventory.player;
        container.startOpen(playerInventory.player);
        if (sourceStack != null) {
            HeldGenerator.OPEN.add(sourceStack);
            itemMode.set(1);
        }

        addSlot(new TemplateSlot(container, 0, 26, 35));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new OutputSlot(container, 1 + col + row * 3, 98 + col * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new CarriedGui.LockableSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18, sourceStack));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new CarriedGui.LockableSlot(playerInventory, col, 8 + col * 18, 142, sourceStack));
        }
        addDataSlots(data);
        addDataSlot(itemMode);
    }

    /** True when this GUI belongs to a Duplicator item rather than a placed block (no "pick up" then). */
    public boolean isItemMode() {
        return itemMode.get() != 0;
    }

    public boolean isEnabled() {
        return data.get(2) != 0;
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
        if (id == BUTTON_PICK_UP && sourceStack == null && player instanceof ServerPlayer serverPlayer) {
            blockEntity.pickUp(serverPlayer);
            return true;
        }
        return false;
    }

    /** Width in pixels of the filled progress arrow. */
    public int getProgressPixels(int total) {
        int progress = data.get(0);
        int max = data.get(1);
        return (max > 0 && progress > 0) ? progress * total / max : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < 10) {
                // template or copies -> player inventory
                if (stack.getMaxStackSize() == 1 && stack.getCount() > 1) {
                    // unstackable items: one per free inventory slot (a normal stack would end up oversized)
                    while (!stack.isEmpty()) {
                        ItemStack one = stack.copy();
                        one.setCount(1);
                        if (!moveItemStackTo(one, 10, 46, true)) {
                            break;
                        }
                        stack.shrink(1);
                    }
                } else if (!moveItemStackTo(stack, 10, 46, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // player inventory -> template slot
                if (!moveItemStackTo(stack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
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

    /** While opened from an item: keep copying and save into the item now and then. */
    @Override
    public void broadcastChanges() {
        if (sourceStack != null && blockEntity != null && !owner.level().isClientSide) {
            DuplicatorBlockEntity.serverTick(owner.level(), BlockPos.ZERO, blockEntity.getBlockState(), blockEntity);
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

    /** Holds exactly one item: the one that gets copied. */
    private static class TemplateSlot extends Slot {
        TemplateSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Copies can be taken but nothing can be placed here. */
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
