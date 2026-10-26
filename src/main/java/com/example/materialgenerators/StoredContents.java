package com.example.materialgenerators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Reads what is stored inside a picked-up generator / duplicator (BlockEntityTag.Items). */
public final class StoredContents {
    private StoredContents() {}

    /** Item id -> total count, plus the grand total. */
    public record Summary(int total, Map<String, Integer> byItem) {}

    public static Summary read(ItemStack stack) {
        Map<String, Integer> byItem = new LinkedHashMap<>();
        int total = 0;
        CompoundTag entity = stack.getTagElement("BlockEntityTag");
        if (entity != null) {
            total += addItems(entity, byItem);
            if (entity.contains("Bays", 9)) {                       // a hub item: add up every bay's pocket
                ListTag bays = entity.getList("Bays", 10);
                for (int i = 0; i < bays.size(); i++) {
                    total += addItems(bays.getCompound(i), byItem);
                }
            }
        }
        return new Summary(total, byItem);
    }

    /** Adds the "Items" list of one tag to the map; returns how many items that was. */
    private static int addItems(CompoundTag tag, Map<String, Integer> byItem) {
        int total = 0;
        if (tag.contains("Items", 9)) {
            ListTag list = tag.getList("Items", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int count = entry.getByte("Count");
                if (count <= 0) {
                    continue;
                }
                total += count;
                byItem.merge(entry.getString("id"), count, Integer::sum);
            }
        }
        return total;
    }

    private static Component nameOf(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return new ItemStack(item == null ? Items.AIR : item).getHoverName();
    }

    /** Adds "Items inside: N" and the biggest piles to a tooltip. */
    public static void addTooltip(ItemStack stack, List<Component> tooltip) {
        Summary summary = read(stack);
        if (summary.total() <= 0) {
            return;
        }
        tooltip.add(Component.literal("Items inside: " + String.format("%,d", summary.total()))
                .withStyle(ChatFormatting.GOLD));
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(summary.byItem().entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < Math.min(4, entries.size()); i++) {
            tooltip.add(Component.literal("  ").append(nameOf(entries.get(i).getKey()))
                    .append(Component.literal(": " + String.format("%,d", entries.get(i).getValue())))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (entries.size() > 4) {
            tooltip.add(Component.literal("  ...and " + (entries.size() - 4) + " more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
