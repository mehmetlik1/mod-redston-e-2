package com.example.materialgenerators;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Lets a generator ITEM produce while a player carries it. The items go into the same "BlockEntityTag.Items"
 * list that a placed generator would load, so nothing changes when you place it.
 *
 * To avoid changing the item every tick (which would resend it to the client and replay the "re-equip"
 * animation), the timer lives in memory; the item is only touched when something is actually produced.
 */
public final class HeldGenerator {
    private static final int SIZE = GeneratorBlockEntity.SIZE;

    /** In-memory timer for one specific item stack (keyed by identity). */
    private static final class Timer {
        long lastTick;
        int progress;
    }

    private static final Map<ItemStack, Timer> TIMERS = new WeakHashMap<>();

    /** Item stacks whose GUI is currently open: the open GUI produces for them, so held production pauses. */
    public static final Set<ItemStack> OPEN = Collections.newSetFromMap(new WeakHashMap<>());

    private HeldGenerator() {}

    public static boolean isEnabled(ItemStack stack) {
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        return tag == null || !tag.contains("Enabled") || tag.getBoolean("Enabled");
    }

    public static void tick(ItemStack stack, GeneratorBlock generator, long gameTime) {
        if (OPEN.contains(stack) || !isEnabled(stack)) {
            return;
        }
        CompoundTag tag = stack.getTagElement("BlockEntityTag");

        GeneratorDef.Variant variant = generator.def().variant(tag == null ? 0 : tag.getInt("Variant"));
        boolean blocks = tag != null && tag.getBoolean("BlockMode") && variant.hasBlock();
        int interval = GeneratorConfig.interval(generator.def(), generator.tier())
                * (blocks ? variant.blockMultiplier() : 1);

        Timer timer = TIMERS.get(stack);
        if (timer == null || timer.lastTick < gameTime - 1) {
            // first tick of being carried, or it was away for a while: start from the saved progress
            timer = new Timer();
            timer.progress = tag == null ? 0 : tag.getInt("Progress");
            TIMERS.put(stack, timer);
        }
        timer.lastTick = gameTime;

        if (timer.progress < interval) {
            timer.progress++;
        }
        if (timer.progress < interval) {
            return;
        }

        Item output = blocks ? variant.resolveBlock() : variant.resolveOutput();
        if (output == Items.AIR) {
            return;
        }

        CompoundTag root = stack.getOrCreateTagElement("BlockEntityTag");
        NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(root, items);
        for (int i = 0; i < SIZE; i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) {
                items.set(i, new ItemStack(output));
            } else if (slot.is(output) && !slot.hasTag() && slot.getCount() < slot.getMaxStackSize()) {
                slot.grow(1);
            } else {
                continue;
            }
            ContainerHelper.saveAllItems(root, items);
            root.putInt("Progress", 0);
            timer.progress = 0;
            return;
        }
        // all 54 slots full: wait (the timer stays at its limit)
    }
}
