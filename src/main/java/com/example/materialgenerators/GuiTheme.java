package com.example.materialgenerators;

import java.io.Reader;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Text/row colours for the mod's GUIs. Read from assets/material_generators/gui_theme.json, so the
 * "Black GUI" resource pack can swap both the textures and the colours.
 */
public final class GuiTheme {
    public static int text = 0xFF404040;
    public static int dim = 0xFF707070;
    public static int accent = 0xFF2E7D32;
    public static int good = 0xFF2E7D32;
    public static int bad = 0xFFB71C1C;
    public static int rowA = 0x1A000000;
    public static int rowB = 0x0A000000;
    public static int rowSelected = 0x5545A045;
    public static int barBackground = 0xFF373737;

    private GuiTheme() {}

    public static void reload(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(new ResourceLocation(MaterialGenerators.MOD_ID, "gui_theme.json"));
        if (resource.isEmpty()) {
            return;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
            text = read(o, "text", text);
            dim = read(o, "dim", dim);
            accent = read(o, "accent", accent);
            good = read(o, "good", good);
            bad = read(o, "bad", bad);
            rowA = read(o, "row_a", rowA);
            rowB = read(o, "row_b", rowB);
            rowSelected = read(o, "row_selected", rowSelected);
            barBackground = read(o, "bar_bg", barBackground);
        } catch (Exception ignored) {
            // keep the previous colours if the file is broken
        }
    }

    private static int read(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        if (e == null) {
            return fallback;
        }
        try {
            String s = e.getAsString().replace("#", "");
            long v = Long.parseLong(s, 16);
            return s.length() <= 6 ? (int) (v | 0xFF000000L) : (int) v;
        } catch (Exception ex) {
            return fallback;
        }
    }
}
