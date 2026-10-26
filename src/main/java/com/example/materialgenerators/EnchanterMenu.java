package com.example.materialgenerators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Slot 0 = item (or book) to enchant, slot 1 = payment (diamonds or netherite ingots),
 * 2-28 = player inventory, 29-37 = hotbar.
 *
 * The list of enchantments is computed the same way on client and server (sorted by registry name),
 * so the screen only sends "row index + action" numbers: id = index * 3 + (0 = lower, 1 = raise), APPLY = enchant.
 * The chosen levels live in DataSlots, which the server syncs to the client.
 */
public class EnchanterMenu extends AbstractContainerMenu {
    public static final int MAX_OPTIONS = 64;
    public static final int APPLY = 1000;

    private final Container container = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            EnchanterMenu.this.slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;
    private final boolean serverSide;
    /** The Enchanter item this GUI was opened from (sneak + right-click), or null for a placed block. */
    @Nullable
    private final ItemStack sourceStack;
    private final DataSlot[] selected = new DataSlot[MAX_OPTIONS];

    private List<Enchantment> options = List.of();
    private ItemStack optionsFor = ItemStack.EMPTY;
    private ItemStack lastItem = ItemStack.EMPTY;

    /** Client side. */
    public EnchanterMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, ContainerLevelAccess.NULL, false, null);
    }

    /** Server side, for a placed Enchanter block. */
    public EnchanterMenu(int id, Inventory playerInventory, ContainerLevelAccess access, boolean serverSide) {
        this(id, playerInventory, access, serverSide, null);
    }

    /** Server side, opened from the Enchanter item "sourceStack". */
    public EnchanterMenu(int id, Inventory playerInventory, ItemStack sourceStack) {
        this(id, playerInventory, ContainerLevelAccess.NULL, true, sourceStack);
    }

    private EnchanterMenu(int id, Inventory playerInventory, ContainerLevelAccess access, boolean serverSide,
                          @Nullable ItemStack sourceStack) {
        super(ModRegistry.ENCHANTER_MENU.get(), id);
        this.access = access;
        this.serverSide = serverSide;
        this.sourceStack = sourceStack;

        addSlot(new Slot(container, 0, 26, 30) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(container, 1, 26, 64) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isPayment(stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 48 + col * 18, 146 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 48 + col * 18, 204));
        }
        for (int i = 0; i < MAX_OPTIONS; i++) {
            selected[i] = DataSlot.standalone();
            addDataSlot(selected[i]);
        }
    }

    public static boolean isPayment(ItemStack stack) {
        return stack.is(Items.DIAMOND) || stack.is(Items.NETHERITE_INGOT);
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() && b.isEmpty();
        }
        return a.getItem() == b.getItem() && Objects.equals(a.getTag(), b.getTag());
    }

    private static boolean bookLike(ItemStack stack) {
        return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
    }

    // ---- state shared by client + server ----

    public ItemStack getItem() {
        return container.getItem(0);
    }

    public ItemStack getPayment() {
        return container.getItem(1);
    }

    public List<Enchantment> getOptions() {
        ItemStack stack = container.getItem(0);
        if (!same(stack, optionsFor)) {
            optionsFor = stack.copy();
            List<Enchantment> list = new ArrayList<>();
            if (!stack.isEmpty()) {
                boolean book = bookLike(stack);
                for (Enchantment e : ForgeRegistries.ENCHANTMENTS) {
                    if (book ? e.isAllowedOnBooks() : e.canEnchant(stack)) {
                        list.add(e);
                    }
                }
                list.sort((a, b) -> String.valueOf(ForgeRegistries.ENCHANTMENTS.getKey(a))
                        .compareTo(String.valueOf(ForgeRegistries.ENCHANTMENTS.getKey(b))));
            }
            options = list.size() > MAX_OPTIONS ? new ArrayList<>(list.subList(0, MAX_OPTIONS)) : list;
        }
        return options;
    }

    public int getSelected(int index) {
        return index >= 0 && index < MAX_OPTIONS ? selected[index].get() : 0;
    }

    public int totalCost() {
        List<Enchantment> opts = getOptions();
        int total = 0;
        for (int i = 0; i < opts.size(); i++) {
            total += EnchantPricing.price(opts.get(i), selected[i].get());
        }
        return total;
    }

    // ---- server-side actions ----

    @Override
    public void slotsChanged(Container changed) {
        super.slotsChanged(changed);
        if (changed == container && serverSide) {
            ItemStack now = container.getItem(0);
            if (!same(now, lastItem)) {
                for (DataSlot slot : selected) {
                    slot.set(0);
                }
                lastItem = now.copy();
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == APPLY) {
            return apply(player);
        }
        int index = id / 3;
        int op = id % 3;
        List<Enchantment> opts = getOptions();
        if (id < 0 || index >= opts.size()) {
            return false;
        }
        Enchantment enchantment = opts.get(index);
        int current = selected[index].get();
        if (op == 0) {
            if (current > 0) {
                selected[index].set(current - 1);
                return true;
            }
            return false;
        }
        if (op == 1 && current < EnchantPricing.maxLevel(enchantment)) {
            if (current == 0) {
                for (int j = 0; j < opts.size(); j++) {
                    if (j != index && selected[j].get() > 0 && !enchantment.isCompatibleWith(opts.get(j))) {
                        selected[j].set(0);
                    }
                }
            }
            selected[index].set(current + 1);
            return true;
        }
        return false;
    }

    private boolean apply(Player player) {
        ItemStack target = container.getItem(0);
        if (target.isEmpty() || target.getCount() != 1) {
            return false;
        }
        List<Enchantment> opts = getOptions();
        int cost = totalCost();
        if (cost <= 0) {
            player.displayClientMessage(Component.literal("Choose at least one enchantment first."), true);
            return false;
        }
        ItemStack pay = container.getItem(1);
        boolean free = player.getAbilities().instabuild;
        int need = 0;
        if (!free) {
            if (pay.is(Items.DIAMOND)) {
                need = cost;
            } else if (pay.is(Items.NETHERITE_INGOT)) {
                need = EnchantPricing.netheriteNeeded(cost);
            } else {
                player.displayClientMessage(Component.literal("Put diamonds or netherite ingots in the payment slot."), true);
                return false;
            }
            if (pay.getCount() < need) {
                player.displayClientMessage(Component.literal("Not enough payment: you need " + need + "."), true);
                return false;
            }
        }

        ItemStack result;
        if (bookLike(target)) {
            result = target.is(Items.BOOK) ? new ItemStack(Items.ENCHANTED_BOOK) : target;
            for (int i = 0; i < opts.size(); i++) {
                if (selected[i].get() > 0) {
                    EnchantedBookItem.addEnchantment(result, new EnchantmentInstance(opts.get(i), selected[i].get()));
                }
            }
        } else {
            result = target;
            Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(result);
            for (int i = 0; i < opts.size(); i++) {
                int level = selected[i].get();
                if (level > 0) {
                    Enchantment chosen = opts.get(i);
                    map.keySet().removeIf(old -> old != chosen && !chosen.isCompatibleWith(old));
                    map.put(chosen, level);
                }
            }
            EnchantmentHelper.setEnchantments(map, result);
        }
        if (need > 0) {
            pay.shrink(need);
        }
        container.setItem(0, result);
        container.setItem(1, pay.isEmpty() ? ItemStack.EMPTY : pay);
        for (DataSlot slot : selected) {
            slot.set(0);
        }
        lastItem = container.getItem(0).copy();
        return true;
    }

    // ---- standard menu plumbing ----

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < 2) {
                if (!moveItemStackTo(stack, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (isPayment(stack)) {
                if (!moveItemStackTo(stack, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
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
        return stillValid(access, player, ModRegistry.ENCHANTER.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (sourceStack != null) {
            clearContainer(player, container);   // hand the item and payment back (there is no block to ask)
        } else {
            access.execute((level, pos) -> clearContainer(player, container));
        }
    }
}
