package com.example.materialgenerators;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Right-click a Storage Crate with this to add a row of 9 slots. The effect itself lives in StorageCrateBlock. */
public class CrateUpgradeItem extends Item {
    public CrateUpgradeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal("Right-click a Storage Crate to add a row of " + StorageCrateBlockEntity.COLUMNS
                + " slots (up to " + StorageCrateBlockEntity.MAX_ROWS * StorageCrateBlockEntity.COLUMNS + ").")
                .withStyle(ChatFormatting.GRAY));
    }
}
