package com.example.materialgenerators;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * BlockItem for generators, the Hub, the Duplicator and the Enchanter: the tooltip shows what is stored inside a
 * picked-up one. Generator items keep producing while carried (see HeldGenerator). Sneak + right-click opens the
 * GUI of any of them straight from the item, without placing it.
 */
public class StoredContentsBlockItem extends BlockItem {
    public StoredContentsBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    private boolean opensFromItem() {
        return getBlock() instanceof GeneratorBlock || getBlock() instanceof GeneratorHubBlock
                || getBlock() instanceof DuplicatorBlock || getBlock() instanceof EnchanterBlock;
    }

    private void openGui(Player player, ItemStack stack) {
        if (player instanceof ServerPlayer serverPlayer) {
            ItemMenus.open(serverPlayer, stack, getBlock());
        }
    }

    /** Sneak + right-click on a block: open the GUI instead of placing. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown() && opensFromItem()) {
            if (!context.getLevel().isClientSide) {
                openGui(player, context.getItemInHand());
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }

    /** Sneak + right-click in the air: open the GUI. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && opensFromItem()) {
            if (!level.isClientSide) {
                openGui(player, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide || !(getBlock() instanceof GeneratorBlock generator) || !(entity instanceof Player player)) {
            return;
        }
        boolean inHand = player.getMainHandItem() == stack || player.getOffhandItem() == stack;
        if (inHand || GeneratorConfig.workAnywhereInInventory()) {
            HeldGenerator.tick(stack, generator, level.getGameTime());
        }
    }

    /**
     * Producing changes the item's data; without this the held item would replay the "re-equip" animation
     * every time that happens. (Forge hook; only switching slots should trigger it.)
     */
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (getBlock() instanceof GeneratorBlock) {
            tooltip.add(Component.literal("Works while held. Sneak + right-click to open it without placing.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (opensFromItem()) {
            tooltip.add(Component.literal("Sneak + right-click to open it without placing.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        CompoundTag entity = stack.getTagElement("BlockEntityTag");
        if (entity == null) {
            return;
        }
        if (getBlock() instanceof GeneratorBlock generator) {
            GeneratorDef.Variant variant = generator.def().variant(entity.getInt("Variant"));
            boolean blocks = entity.getBoolean("BlockMode") && variant.hasBlock();
            Item made = blocks ? variant.resolveBlock() : variant.resolveOutput();
            if (generator.def().variants().size() > 1 || blocks) {
                tooltip.add(Component.literal("Making: ").append(new ItemStack(made).getHoverName())
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        if (getBlock() instanceof GeneratorHubBlock && entity.contains("Bays", 9)) {
            ListTag bays = entity.getList("Bays", 10);
            int installed = 0;
            for (int i = 0; i < bays.size(); i++) {
                if (!bays.getCompound(i).getString("Gen").isEmpty()) {
                    installed++;
                }
            }
            int total = entity.contains("BayCount") ? entity.getInt("BayCount") : GeneratorHubBlockEntity.BASE_BAYS;
            tooltip.add(Component.literal("Generators installed: " + installed + "/" + total)
                    .withStyle(ChatFormatting.AQUA));
        }
        if (entity.contains("Enabled") && !entity.getBoolean("Enabled") && getBlock() instanceof GeneratorBlock) {
            tooltip.add(Component.literal("Switched off").withStyle(ChatFormatting.RED));
        }
        StoredContents.addTooltip(stack, tooltip);
    }
}
