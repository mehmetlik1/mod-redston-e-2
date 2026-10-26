package com.example.materialgenerators;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Helpers for GUIs that are opened from a generator / hub ITEM (sneak + right-click) instead of a placed block. */
public final class CarriedGui {
    private CarriedGui() {}

    /** True while the player still has exactly this item stack somewhere in their inventory. */
    public static boolean isCarrying(Player player, ItemStack stack) {
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) {
            if (s == stack) {
                return true;
            }
        }
        for (ItemStack s : inv.offhand) {
            if (s == stack) {
                return true;
            }
        }
        return false;
    }

    /** A player-inventory slot that cannot pick up the item whose GUI is open (so it cannot be lost or copied). */
    public static class LockableSlot extends Slot {
        private final ItemStack locked;

        public LockableSlot(Container container, int index, int x, int y, ItemStack locked) {
            super(container, index, x, y);
            this.locked = locked;
        }

        @Override
        public boolean mayPickup(Player player) {
            return locked == null || getItem() != locked;
        }
    }
}
