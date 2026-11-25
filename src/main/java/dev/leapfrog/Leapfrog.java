package dev.leapfrog;

import dev.leapfrog.assets.FutureAssets;
import dev.leapfrog.block.FutureBlock;
import dev.leapfrog.block.FutureBlocks;
import dev.leapfrog.cushion.CushionColors;
import dev.leapfrog.geyser.GeyserLogic;
import dev.leapfrog.geyser.GeyserParticles;
import dev.leapfrog.sound.CushionSounds;
import dev.leapfrog.sulfur.SulfurCubeData;
import dev.leapfrog.ui.SettingsScreen;
import dev.leapfrog.via.ViaBridge;
import dev.leapfrog.via.ViaFabricControl;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Leapfrog implements ClientModInitializer {
    public static final String MOD_ID = "leapfrog";
    public static final Logger LOG = LoggerFactory.getLogger("Leapfrog");

    // 26.x blocks with transparent pixels in their textures.
    private static final Set<String> CUTOUT = Set.of(
            "golden_dandelion", "potted_golden_dandelion", "poplar_sapling", "potted_poplar_sapling",
            "red_poplar_leaves", "orange_poplar_leaves", "yellow_poplar_leaves", "poplar_door", "poplar_trapdoor",
            "red_shrub", "straw_bed", "sulfur_spike");

    @Override
    public void onInitializeClient() {
        for (FutureBlock block : FutureBlocks.all().values()) {
            if (CUTOUT.contains(block.info().name())) BlockRenderLayerMap.putBlock(block, ChunkSectionLayer.CUTOUT);
        }

        GeyserParticles.registerProviders();
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> GeyserLogic.onChunkLoad(chunk));
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> GeyserLogic.onChunkUnload(chunk));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GeyserLogic.clear();
            CushionColors.clear();
            SulfurCubeData.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(GeyserLogic::tick);
        ClientTickEvents.END_CLIENT_TICK.register(CushionSounds::tick);
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            CushionSounds.onEntityUnload(entity);
            CushionColors.remove(entity.getId());
            SulfurCubeData.remove(entity.getId());
        });

        KeyMapping openSettings = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.leapfrog.settings", GLFW.GLFW_KEY_K,
                new KeyMapping.Category(Identifier.fromNamespaceAndPath(MOD_ID, "main"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettings.consumeClick()) client.setScreen(new SettingsScreen(client.screen));
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (FutureAssets.failed()) SystemToast.add(client.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("Leapfrog: 26.3 assets missing"),
                    Component.literal("Download failed. Check your connection and restart the game."));
        });

        if (Features.viaBackwardsPresent()) {
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> ViaBridge.tryInstall());
            ClientLoginConnectionEvents.INIT.register((handler, client) -> ViaBridge.tryInstall());
            ClientPlayConnectionEvents.INIT.register((handler, client) -> ViaBridge.tryInstall());
            ClientTickEvents.END_CLIENT_TICK.register(client -> ViaBridge.tick());
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ViaFabricControl.restore());
            LOG.info("ViaBackwards found, placed 26.x blocks will be restored on 26.3 connections");
        }
    }
}
