package com.example.materialgenerators;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MaterialGenerators.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModRegistry.DUPLICATOR_MENU.get(), DuplicatorScreen::new);
            MenuScreens.register(ModRegistry.GENERATOR_MENU.get(), GeneratorScreen::new);
            MenuScreens.register(ModRegistry.ENCHANTER_MENU.get(), EnchanterScreen::new);
            MenuScreens.register(ModRegistry.HUB_MENU.get(), GeneratorHubScreen::new);
        });
    }

    /** Reloads the GUI colours whenever resource packs change (e.g. the Black GUI pack is switched on). */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) GuiTheme::reload);
    }
}
