package com.example.materialgenerators;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Shows "Items inside: N" above the hotbar while you hold a generator / duplicator that has items stored in it. */
@Mod.EventBusSubscriber(modid = MaterialGenerators.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HeldContentsHud {
    private HeldContentsHud() {}

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof StoredContentsBlockItem)) {
            held = player.getOffhandItem();
            if (!(held.getItem() instanceof StoredContentsBlockItem)) {
                return;
            }
        }
        int total = StoredContents.read(held).total();
        if (total <= 0) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int x = event.getWindow().getGuiScaledWidth() / 2;
        int y = event.getWindow().getGuiScaledHeight() - 72;
        graphics.drawCenteredString(mc.font, "Items inside: " + String.format("%,d", total), x, y, 0xFFD27F);
    }
}
