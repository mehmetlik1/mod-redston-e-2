package com.example.materialgenerators;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Right-click a Generator Hub with this to unlock more bays. The effect itself lives in GeneratorHubBlock. */
public class HubExpansionItem extends Item {
    public HubExpansionItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal("Right-click a Generator Hub to add " + GeneratorHubBlockEntity.KIT_BAYS
                + " bays (up to " + GeneratorHubBlockEntity.MAX_BAYS + ").").withStyle(ChatFormatting.GRAY));
    }
}
