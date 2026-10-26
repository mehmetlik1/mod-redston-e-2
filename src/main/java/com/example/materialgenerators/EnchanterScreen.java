package com.example.materialgenerators;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

public class EnchanterScreen extends AbstractContainerScreen<EnchanterMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MaterialGenerators.MOD_ID, "textures/gui/enchanter.png");

    private static final int ROWS = 7;
    private static final int ROW_H = 16;
    private static final int LIST_X = 72;
    private static final int LIST_Y = 18;
    private static final int LIST_W = 166;
    private static final int MINUS_X = 190;
    private static final int PLUS_X = 224;
    private static final int[] ENCHANT_BUTTON = {9, 88, 56, 16};

    private int scroll = 0;

    public EnchanterScreen(EnchanterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 230;
        this.inventoryLabelX = 48;
        this.inventoryLabelY = 134;
    }

    private boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + y && my < topPos + y + h;
    }

    private int maxScroll() {
        return Math.max(0, menu.getOptions().size() - ROWS);
    }

    private boolean canPay(int cost) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getAbilities().instabuild) {
            return true;
        }
        ItemStack pay = menu.getPayment();
        if (pay.is(Items.DIAMOND)) {
            return pay.getCount() >= cost;
        }
        return pay.is(Items.NETHERITE_INGOT) && pay.getCount() >= EnchantPricing.netheriteNeeded(cost);
    }

    private void small(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, text, (int) ((leftPos + x) / 0.75F), (int) ((topPos + y) / 0.75F), color, false);
        g.pose().popPose();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        List<Enchantment> options = menu.getOptions();

        // enchantment rows
        for (int r = 0; r < ROWS; r++) {
            int index = scroll + r;
            int y = topPos + LIST_Y + r * ROW_H;
            if (index >= options.size()) {
                break;
            }
            Enchantment e = options.get(index);
            int level = menu.getSelected(index);
            g.fill(leftPos + LIST_X, y, leftPos + LIST_X + LIST_W, y + ROW_H, level > 0 ? GuiTheme.rowSelected
                    : (index % 2 == 0 ? GuiTheme.rowA : GuiTheme.rowB));

            String name = Component.translatable(e.getDescriptionId()).getString();
            g.drawString(font, font.plainSubstrByWidth(name, 86), leftPos + LIST_X + 4, y + 4,
                    level > 0 ? GuiTheme.text : GuiTheme.dim, false);

            String price = String.valueOf(EnchantPricing.price(e, Math.max(1, level)));
            g.drawString(font, price, leftPos + 186 - font.width(price), y + 4,
                    level > 0 ? GuiTheme.text : GuiTheme.dim, false);

            boolean minusOn = level > 0;
            boolean plusOn = level < EnchantPricing.maxLevel(e);
            int minusU = !minusOn ? 192 : over(mouseX, mouseY, MINUS_X, y - topPos + 2, 12, 12) ? 180 : 168;
            int plusU = !plusOn ? 192 : over(mouseX, mouseY, PLUS_X, y - topPos + 2, 12, 12) ? 180 : 168;
            g.blit(TEXTURE, leftPos + MINUS_X, y + 2, minusU, 230, 12, 12);
            g.blit(TEXTURE, leftPos + PLUS_X, y + 2, plusU, 230, 12, 12);
            g.drawCenteredString(font, "-", leftPos + MINUS_X + 6, y + 4, minusOn ? GuiTheme.text : GuiTheme.dim);
            g.drawCenteredString(font, "+", leftPos + PLUS_X + 6, y + 4, plusOn ? GuiTheme.text : GuiTheme.dim);
            g.drawCenteredString(font, String.valueOf(level), leftPos + 213, y + 4, level > 0 ? GuiTheme.accent : GuiTheme.dim);
        }
        if (options.isEmpty()) {
            g.drawString(font, "Put an item in the", leftPos + LIST_X + 8, topPos + LIST_Y + 8, GuiTheme.dim, false);
            g.drawString(font, "left slot to see its", leftPos + LIST_X + 8, topPos + LIST_Y + 20, GuiTheme.dim, false);
            g.drawString(font, "enchantments.", leftPos + LIST_X + 8, topPos + LIST_Y + 32, GuiTheme.dim, false);
        }

        // scroll bar
        int trackX = leftPos + 241, trackY = topPos + LIST_Y, trackH = ROWS * ROW_H;
        if (maxScroll() > 0) {
            int handleH = Math.max(10, trackH * ROWS / options.size());
            int handleY = trackY + (trackH - handleH) * scroll / maxScroll();
            g.fill(trackX, handleY, trackX + 5, handleY + handleH, GuiTheme.dim);
        }

        // enchant button + cost
        int cost = menu.totalCost();
        boolean ready = cost > 0 && !menu.getItem().isEmpty();
        boolean hover = ready && over(mouseX, mouseY, ENCHANT_BUTTON[0], ENCHANT_BUTTON[1], ENCHANT_BUTTON[2], ENCHANT_BUTTON[3]);
        g.blit(TEXTURE, leftPos + ENCHANT_BUTTON[0], topPos + ENCHANT_BUTTON[1], !ready ? 112 : hover ? 56 : 0, 230, 56, 16);
        g.drawCenteredString(font, "Enchant", leftPos + ENCHANT_BUTTON[0] + 28, topPos + ENCHANT_BUTTON[1] + 4,
                ready ? GuiTheme.text : GuiTheme.dim);
        if (cost > 0) {
            int color = canPay(cost) ? GuiTheme.good : GuiTheme.bad;
            small(g, cost + " diamonds", 11, 110, color);
            small(g, "or " + EnchantPricing.netheriteNeeded(cost) + " netherite", 11, 120, color);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, GuiTheme.text, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiTheme.text, false);
        g.drawCenteredString(font, "Item", 34, 19, GuiTheme.dim);
        g.drawCenteredString(font, "Payment", 34, 53, GuiTheme.dim);
        g.drawString(font, "Cost", 168, 7, GuiTheme.dim, false);
        g.drawString(font, "Level", 200, 7, GuiTheme.dim, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);

        List<Enchantment> options = menu.getOptions();
        for (int r = 0; r < ROWS; r++) {
            int index = scroll + r;
            if (index >= options.size()) {
                break;
            }
            if (over(mouseX, mouseY, LIST_X, LIST_Y + r * ROW_H, 112, ROW_H)) {
                Enchantment e = options.get(index);
                int max = EnchantPricing.maxLevel(e);
                int level = Math.max(1, menu.getSelected(index));
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable(e.getDescriptionId()));
                tip.add(Component.literal("Max level here: " + max));
                tip.add(Component.literal("Level " + level + " costs " + EnchantPricing.price(e, level) + " diamonds"));
                g.renderComponentTooltip(font, tip, mouseX, mouseY);
            }
        }
        if (over(mouseX, mouseY, 6, 108, 60, 22)) {
            g.renderComponentTooltip(font, List.of(
                    Component.literal("1 netherite ingot = " + EnchantPricing.NETHERITE_VALUE + " diamonds"),
                    Component.literal("Pay with either one.")), mouseX, mouseY);
        }
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            List<Enchantment> options = menu.getOptions();
            for (int r = 0; r < ROWS; r++) {
                int index = scroll + r;
                if (index >= options.size()) {
                    break;
                }
                int rowY = LIST_Y + r * ROW_H + 2;
                if (over(mx, my, MINUS_X, rowY, 12, 12)) {
                    send(index * 3);
                    return true;
                }
                if (over(mx, my, PLUS_X, rowY, 12, 12)) {
                    send(index * 3 + 1);
                    return true;
                }
            }
            if (over(mx, my, ENCHANT_BUTTON[0], ENCHANT_BUTTON[1], ENCHANT_BUTTON[2], ENCHANT_BUTTON[3])
                    && menu.totalCost() > 0) {
                send(EnchanterMenu.APPLY);
                return true;
            }
            if (maxScroll() > 0 && over(mx, my, 240, LIST_Y, 8, ROWS * ROW_H)) {
                double t = (my - topPos - LIST_Y) / (double) (ROWS * ROW_H);
                scroll = (int) Math.round(t * maxScroll());
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (over(mx, my, LIST_X, LIST_Y, LIST_W + 8, ROWS * ROW_H)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
}
