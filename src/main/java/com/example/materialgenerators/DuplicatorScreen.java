package com.example.materialgenerators;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

public class DuplicatorScreen extends AbstractContainerScreen<DuplicatorMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MaterialGenerators.MOD_ID, "textures/gui/duplicator.png");
    // button rectangles relative to the panel: x, y, width, height
    private static final int[] TOGGLE = {6, 58, 44, 16};
    private static final int[] PICK_UP = {52, 58, 44, 16};

    public DuplicatorScreen(DuplicatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    private boolean over(double mx, double my, int[] r) {
        return mx >= leftPos + r[0] && mx < leftPos + r[0] + r[2] && my >= topPos + r[1] && my < topPos + r[1] + r[3];
    }

    private void button(GuiGraphics g, int[] r, int mouseX, int mouseY, String label, int color) {
        g.blit(TEXTURE, leftPos + r[0], topPos + r[1], 176, over(mouseX, mouseY, r) ? 36 : 20, r[2], r[3]);
        g.drawCenteredString(font, label, leftPos + r[0] + r[2] / 2, topPos + r[1] + 4, color);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int width = menu.getProgressPixels(24);
        if (width > 0) {
            graphics.blit(TEXTURE, leftPos + 56, topPos + 34, 176, 0, width, 17);
        }
        boolean on = menu.isEnabled();
        button(graphics, TOGGLE, mouseX, mouseY, on ? "Stop" : "Start", on ? GuiTheme.bad : GuiTheme.good);
        button(graphics, PICK_UP, mouseX, mouseY, "Pick up", menu.isItemMode() ? GuiTheme.dim : GuiTheme.text);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, GuiTheme.text, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiTheme.text, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (over(mouseX, mouseY, TOGGLE)) {
            graphics.renderTooltip(font, Component.literal(menu.isEnabled() ? "Stop copying" : "Start copying"), mouseX, mouseY);
        } else if (over(mouseX, mouseY, PICK_UP)) {
            graphics.renderTooltip(font, Component.literal(menu.isItemMode()
                    ? "This Duplicator is already an item in your inventory"
                    : "Take the Duplicator with its items inside (nothing drops)"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            int id = over(mx, my, TOGGLE) ? DuplicatorMenu.BUTTON_TOGGLE
                    : over(mx, my, PICK_UP) && !menu.isItemMode() ? DuplicatorMenu.BUTTON_PICK_UP : -1;
            if (id >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
