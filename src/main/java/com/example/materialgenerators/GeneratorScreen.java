package com.example.materialgenerators;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GeneratorScreen extends AbstractContainerScreen<GeneratorMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MaterialGenerators.MOD_ID, "textures/gui/generator.png");
    // button rectangles relative to the panel: x, y, width, height
    private static final int[] TOGGLE = {8, 17, 44, 16};
    private static final int[] MODE = {54, 17, 44, 16};
    private static final int[] VARIANT = {100, 16, 18, 18};   // shows the item being made; click = next material
    private static final int[] PICK_UP = {120, 17, 44, 16};

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 250;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    private boolean over(double mx, double my, int[] r) {
        return mx >= leftPos + r[0] && mx < leftPos + r[0] + r[2] && my >= topPos + r[1] && my < topPos + r[1] + r[3];
    }

    private void button(GuiGraphics g, int[] r, int mouseX, int mouseY, boolean enabled, String label, int color) {
        int v = !enabled ? 32 : over(mouseX, mouseY, r) ? 16 : 0;
        g.blit(TEXTURE, leftPos + r[0], topPos + r[1], 176, v, r[2], r[3]);
        g.drawCenteredString(font, label, leftPos + r[0] + r[2] / 2, topPos + r[1] + 4, color);
    }

    private GeneratorDef def() {
        List<GeneratorDef> all = GeneratorDef.ALL;
        int index = menu.getDefIndex();
        return all.get(index >= 0 && index < all.size() ? index : 0);
    }

    /** The item this generator is making right now (item or block form of the selected material). */
    private Item madeItem() {
        GeneratorDef.Variant variant = def().variant(menu.getVariantIndex());
        return menu.isBlockMode() ? variant.resolveBlock() : variant.resolveOutput();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        boolean on = menu.isEnabled();
        button(g, TOGGLE, mouseX, mouseY, true, on ? "ON" : "OFF", on ? GuiTheme.good : GuiTheme.bad);

        boolean hasBlocks = menu.hasBlockForm();
        button(g, MODE, mouseX, mouseY, hasBlocks, menu.isBlockMode() ? "Blocks" : "Items",
                hasBlocks ? GuiTheme.text : GuiTheme.dim);

        boolean canSwitch = def().variants().size() > 1;
        int v = !canSwitch ? 80 : over(mouseX, mouseY, VARIANT) ? 66 : 48;
        g.blit(TEXTURE, leftPos + VARIANT[0], topPos + VARIANT[1], 176, v, VARIANT[2], VARIANT[3]);
        g.renderItem(new ItemStack(madeItem()), leftPos + VARIANT[0] + 1, topPos + VARIANT[1] + 1);

        button(g, PICK_UP, mouseX, mouseY, !menu.isItemMode(), "Pick up", menu.isItemMode() ? GuiTheme.dim : GuiTheme.text);

        // progress bar along the bottom of the toolbar
        int bx = leftPos + 9, by = topPos + 36, bw = 158, bh = 4;
        g.fill(bx, by, bx + bw, by + bh, GuiTheme.barBackground);
        int interval = Math.max(1, menu.getInterval());
        int filled = Math.min(bw - 2, (bw - 2) * menu.getProgress() / interval);
        if (filled > 0) {
            g.fill(bx + 1, by + 1, bx + 1 + filled, by + bh - 1, on ? GuiTheme.accent : GuiTheme.dim);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, font.plainSubstrByWidth(title.getString(), 160), titleLabelX, titleLabelY, GuiTheme.text, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiTheme.text, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (over(mouseX, mouseY, TOGGLE)) {
            g.renderTooltip(font, Component.literal(menu.isEnabled() ? "Switch off" : "Switch on"), mouseX, mouseY);
        } else if (over(mouseX, mouseY, MODE)) {
            String text = !menu.hasBlockForm() ? "This material has no block form"
                    : menu.isBlockMode() ? "Making blocks (slower). Click to make items again"
                    : "Making items. Click to make blocks instead (slower)";
            g.renderTooltip(font, Component.literal(text), mouseX, mouseY);
        } else if (over(mouseX, mouseY, VARIANT)) {
            Component name = new ItemStack(madeItem()).getHoverName();
            Component tip = def().variants().size() > 1
                    ? Component.literal("Making: ").append(name).append(Component.literal(" (click to switch material)"))
                    : Component.literal("Making: ").append(name);
            g.renderTooltip(font, tip, mouseX, mouseY);
        } else if (over(mouseX, mouseY, PICK_UP)) {
            g.renderTooltip(font, Component.literal(menu.isItemMode()
                    ? "This generator is already an item in your inventory"
                    : "Take the generator with its items inside (nothing drops)"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            int id = -1;
            if (over(mx, my, TOGGLE)) {
                id = GeneratorMenu.BUTTON_TOGGLE;
            } else if (over(mx, my, MODE) && menu.hasBlockForm()) {
                id = GeneratorMenu.BUTTON_MODE;
            } else if (over(mx, my, VARIANT) && def().variants().size() > 1) {
                id = GeneratorMenu.BUTTON_VARIANT;
            } else if (over(mx, my, PICK_UP) && !menu.isItemMode()) {
                id = GeneratorMenu.BUTTON_PICK_UP;
            }
            if (id >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
