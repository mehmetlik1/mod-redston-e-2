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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GeneratorHubScreen extends AbstractContainerScreen<GeneratorHubMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MaterialGenerators.MOD_ID, "textures/gui/generator_hub.png");
    /** How many bay tabs fit next to the panel; with more bays the tabs scroll. */
    private static final int VISIBLE = 6;
    // rectangles relative to the panel: x, y, width, height
    private static final int[] TOGGLE = {8, 17, 44, 16};
    private static final int[] MODE = {54, 17, 44, 16};
    private static final int[] VARIANT = {100, 16, 18, 18};
    private static final int[] INSTALL = {146, 16, 18, 18};   // shows the generator in this bay
    private static final int[] ALL = {119, 17, 26, 16};       // stop / start every generator in the hub
    private static final int[] SCROLL_UP = {176, 6, 26, 12};
    private static final int[] SCROLL_DOWN = {176, 178, 26, 12};

    private int tabScroll = 0;

    public GeneratorHubScreen(GeneratorHubMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 250;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    private boolean over(double mx, double my, int[] r) {
        return mx >= leftPos + r[0] && mx < leftPos + r[0] + r[2] && my >= topPos + r[1] && my < topPos + r[1] + r[3];
    }

    private int maxScroll() {
        return Math.max(0, menu.getBayCount() - VISIBLE);
    }

    /** Is at least one installed generator switched on? (decides whether the ALL button stops or starts) */
    private boolean anyRunning() {
        for (int i = 0; i < menu.getBayCount(); i++) {
            if (menu.bayGenerator(i) != null && menu.bayEnabled(i)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyInstalled() {
        for (int i = 0; i < menu.getBayCount(); i++) {
            if (menu.bayGenerator(i) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean scrolls() {
        return menu.getBayCount() > VISIBLE;
    }

    /** Rectangle of the tab drawn in row 0..VISIBLE-1. */
    private int[] tab(int row) {
        return new int[]{176, 20 + row * 26, 26, 24};
    }

    private void button(GuiGraphics g, int[] r, int mouseX, int mouseY, boolean enabled, String label, int color) {
        int v = !enabled ? 32 : over(mouseX, mouseY, r) ? 16 : 0;
        g.blit(TEXTURE, leftPos + r[0], topPos + r[1], 176, v, r[2], r[3]);
        g.drawCenteredString(font, label, leftPos + r[0] + r[2] / 2, topPos + r[1] + 4, color);
    }

    /** The item the selected bay is making right now (item or block form of the selected material). */
    private Item madeItem() {
        GeneratorBlock gen = menu.bayGenerator(menu.getSelectedBay());
        if (gen == null) {
            return Items.AIR;
        }
        GeneratorDef.Variant variant = gen.def().variant(menu.selectedVariant());
        return menu.selectedMakesBlocks() ? variant.resolveBlock() : variant.resolveOutput();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        tabScroll = Math.max(0, Math.min(tabScroll, maxScroll()));
        int sel = menu.getSelectedBay();
        int count = menu.getBayCount();

        // bay tabs on the right (scrollable)
        for (int row = 0; row < VISIBLE; row++) {
            int bay = tabScroll + row;
            if (bay >= count) {
                break;
            }
            int[] t = tab(row);
            int v = bay == sel ? 156 : over(mouseX, mouseY, t) ? 130 : 104;
            g.blit(TEXTURE, leftPos + t[0], topPos + t[1], 176, v, t[2], t[3]);
            GeneratorBlock tabGen = menu.bayGenerator(bay);
            if (tabGen != null) {
                g.renderItem(new ItemStack(tabGen.asItem()), leftPos + t[0] + 5, topPos + t[1] + 4);
            } else {
                g.drawCenteredString(font, "+", leftPos + t[0] + 13, topPos + t[1] + 8, GuiTheme.dim);
            }
        }
        if (scrolls()) {
            g.blit(TEXTURE, leftPos + SCROLL_UP[0], topPos + SCROLL_UP[1], over(mouseX, mouseY, SCROLL_UP) ? 202 : 176, 182, 26, 12);
            g.blit(TEXTURE, leftPos + SCROLL_DOWN[0], topPos + SCROLL_DOWN[1], over(mouseX, mouseY, SCROLL_DOWN) ? 202 : 176, 196, 26, 12);
        }

        GeneratorBlock gen = menu.bayGenerator(sel);
        boolean has = gen != null;

        boolean on = menu.bayEnabled(sel);
        button(g, TOGGLE, mouseX, mouseY, has, !has ? "-" : on ? "ON" : "OFF", !has ? GuiTheme.dim : on ? GuiTheme.good : GuiTheme.bad);
        boolean hasBlocks = has && menu.selectedHasBlockForm();
        button(g, MODE, mouseX, mouseY, hasBlocks, menu.selectedMakesBlocks() ? "Blocks" : "Items",
                hasBlocks ? GuiTheme.text : GuiTheme.dim);

        boolean canSwitch = has && gen.def().variants().size() > 1;
        int v = !canSwitch ? 84 : over(mouseX, mouseY, VARIANT) ? 66 : 48;
        g.blit(TEXTURE, leftPos + VARIANT[0], topPos + VARIANT[1], 176, v, VARIANT[2], VARIANT[3]);
        if (has) {
            g.renderItem(new ItemStack(madeItem()), leftPos + VARIANT[0] + 1, topPos + VARIANT[1] + 1);
            g.renderItem(new ItemStack(gen.asItem()), leftPos + INSTALL[0] + 1, topPos + INSTALL[1] + 1);
        }

        // one button for the whole hub
        boolean installed = anyInstalled();
        boolean running = anyRunning();
        int allV = !installed ? 228 : over(mouseX, mouseY, ALL) ? 202 : 176;
        g.blit(TEXTURE, leftPos + ALL[0], topPos + ALL[1], allV, 210, ALL[2], ALL[3]);
        g.drawCenteredString(font, running ? "Stop" : "Go", leftPos + ALL[0] + ALL[2] / 2, topPos + ALL[1] + 4,
                !installed ? GuiTheme.dim : running ? GuiTheme.bad : GuiTheme.good);

        // progress bar along the bottom of the toolbar
        int bx = leftPos + 9, by = topPos + 36, bw = 158, bh = 4;
        g.fill(bx, by, bx + bw, by + bh, GuiTheme.barBackground);
        if (has) {
            int interval = Math.max(1, menu.selectedInterval());
            int filled = Math.min(bw - 2, (bw - 2) * menu.selectedProgress() / interval);
            if (filled > 0) {
                g.fill(bx + 1, by + 1, bx + 1 + filled, by + bh - 1, on ? GuiTheme.accent : GuiTheme.dim);
            }
        } else {
            // empty bay: dim the pocket and explain
            g.fill(leftPos + 7, topPos + 45, leftPos + 169, topPos + 153, 0xB0000000);
            g.drawCenteredString(font, "Empty bay " + (sel + 1), leftPos + 88, topPos + 86, 0xFFFFFFFF);
            g.drawCenteredString(font, "Click the frame (top right) with a", leftPos + 88, topPos + 100, 0xFFC8C8C8);
            g.drawCenteredString(font, "generator on your cursor, or", leftPos + 88, topPos + 111, 0xFFC8C8C8);
            g.drawCenteredString(font, "shift-click one from your inventory.", leftPos + 88, topPos + 122, 0xFFC8C8C8);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, font.plainSubstrByWidth(title.getString(), 110), titleLabelX, titleLabelY, GuiTheme.text, false);
        String bays = menu.getBayCount() + "/" + GeneratorHubBlockEntity.MAX_BAYS + " bays";
        g.drawString(font, bays, 168 - font.width(bays), titleLabelY, GuiTheme.dim, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiTheme.text, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);

        int sel = menu.getSelectedBay();
        GeneratorBlock gen = menu.bayGenerator(sel);
        for (int row = 0; row < VISIBLE; row++) {
            int bay = tabScroll + row;
            if (bay >= menu.getBayCount()) {
                break;
            }
            if (over(mouseX, mouseY, tab(row))) {
                GeneratorBlock tabGen = menu.bayGenerator(bay);
                List<Component> tip = new ArrayList<>();
                if (tabGen == null) {
                    tip.add(Component.literal("Bay " + (bay + 1) + ": empty"));
                } else {
                    tip.add(new ItemStack(tabGen.asItem()).getHoverName());
                    if (!menu.bayEnabled(bay)) {
                        tip.add(Component.literal("Switched off"));
                    }
                }
                g.renderComponentTooltip(font, tip, mouseX, mouseY);
                return;
            }
        }
        if (scrolls() && (over(mouseX, mouseY, SCROLL_UP) || over(mouseX, mouseY, SCROLL_DOWN))) {
            g.renderTooltip(font, Component.literal("Scroll the bay tabs (or use the mouse wheel)"), mouseX, mouseY);
        } else if (over(mouseX, mouseY, TOGGLE) && gen != null) {
            g.renderTooltip(font, Component.literal(menu.bayEnabled(sel) ? "Switch this generator off" : "Switch this generator on"), mouseX, mouseY);
        } else if (over(mouseX, mouseY, MODE)) {
            String text = gen == null ? "No generator in this bay"
                    : !menu.selectedHasBlockForm() ? "This material has no block form"
                    : menu.selectedMakesBlocks() ? "Making blocks (slower). Click to make items again"
                    : "Making items. Click to make blocks instead (slower)";
            g.renderTooltip(font, Component.literal(text), mouseX, mouseY);
        } else if (over(mouseX, mouseY, VARIANT) && gen != null) {
            Component name = new ItemStack(madeItem()).getHoverName();
            g.renderTooltip(font, gen.def().variants().size() > 1
                    ? Component.literal("Making: ").append(name).append(Component.literal(" (click to switch material)"))
                    : Component.literal("Making: ").append(name), mouseX, mouseY);
        } else if (over(mouseX, mouseY, ALL)) {
            g.renderTooltip(font, Component.literal(!anyInstalled() ? "No generators installed"
                    : anyRunning() ? "Stop ALL generators in this hub" : "Start ALL generators in this hub"), mouseX, mouseY);
        } else if (over(mouseX, mouseY, INSTALL)) {
            g.renderTooltip(font, Component.literal(gen == null
                    ? "Click with a generator on your cursor to install it"
                    : "Click to take this generator out (its items stay inside it)"), mouseX, mouseY);
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
            if (scrolls()) {
                if (over(mx, my, SCROLL_UP)) {
                    tabScroll = Math.max(0, tabScroll - 1);
                    return true;
                }
                if (over(mx, my, SCROLL_DOWN)) {
                    tabScroll = Math.min(maxScroll(), tabScroll + 1);
                    return true;
                }
            }
            for (int row = 0; row < VISIBLE; row++) {
                int bay = tabScroll + row;
                if (bay >= menu.getBayCount()) {
                    break;
                }
                if (over(mx, my, tab(row))) {
                    send(GeneratorHubMenu.BUTTON_SELECT + bay);
                    return true;
                }
            }
            int sel = menu.getSelectedBay();
            GeneratorBlock gen = menu.bayGenerator(sel);
            if (over(mx, my, ALL) && anyInstalled()) {
                send(GeneratorHubMenu.BUTTON_TOGGLE_ALL);
                return true;
            }
            if (over(mx, my, INSTALL)) {
                send(GeneratorHubMenu.BUTTON_INSTALL);
                return true;
            }
            if (gen != null) {
                if (over(mx, my, TOGGLE)) {
                    send(GeneratorHubMenu.BUTTON_TOGGLE);
                    return true;
                }
                if (over(mx, my, MODE) && menu.selectedHasBlockForm()) {
                    send(GeneratorHubMenu.BUTTON_MODE);
                    return true;
                }
                if (over(mx, my, VARIANT) && gen.def().variants().size() > 1) {
                    send(GeneratorHubMenu.BUTTON_VARIANT);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (scrolls() && mx >= leftPos + 176 && mx < leftPos + 202 && my >= topPos && my < topPos + 192) {
            tabScroll = Math.max(0, Math.min(maxScroll(), tabScroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
}
