package dev.leapfrog;

import dev.leapfrog.assets.FutureAssets;
import dev.leapfrog.block.FutureBlocks;
import dev.leapfrog.geyser.GeyserParticleTypes;
import dev.leapfrog.sound.FutureSoundRegistry;
import net.fabricmc.api.ModInitializer;

// Registries freeze before client entrypoints run, so registration lives here.
public final class LeapfrogMain implements ModInitializer {
    @Override
    public void onInitialize() {
        Config.load();
        FutureAssets.start();
        FutureBlocks.register();
        FutureSoundRegistry.register();
        GeyserParticleTypes.register();
    }
}
