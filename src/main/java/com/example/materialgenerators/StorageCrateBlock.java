package com.example.materialgenerators;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/** The Storage Crate: right-click to open, right-click with a Crate Upgrade to add a row of 9 slots. */
public class StorageCrateBlock extends BaseEntityBlock {
    public StorageCrateBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageCrateBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof StorageCrateBlockEntity crate)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModRegistry.CRATE_UPGRADE.get())) {
            if (crate.upgrade()) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                player.displayClientMessage(Component.literal("Storage Crate upgraded: " + crate.getContainerSize() + " slots"), true);
            } else {
                player.displayClientMessage(Component.literal("This crate is fully upgraded ("
                        + crate.getContainerSize() + " slots)"), true);
            }
            return InteractionResult.CONSUME;
        }
        if (be instanceof MenuProvider provider) {
            player.openMenu(provider);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StorageCrateBlockEntity crate) {
                Containers.dropContents(level, pos, crate);
                int upgrades = crate.getRows() - StorageCrateBlockEntity.BASE_ROWS;   // the upgrades are not lost
                if (upgrades > 0) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            new ItemStack(ModRegistry.CRATE_UPGRADE.get(), upgrades));
                }
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }
}
