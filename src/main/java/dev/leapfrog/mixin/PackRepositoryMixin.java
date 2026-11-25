package dev.leapfrog.mixin;

import com.google.common.collect.ImmutableSet;
import dev.leapfrog.assets.FuturePack;
import java.util.Set;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Adds the 26.x asset pack to the client's resource pack repository (the one holding the vanilla pack).
@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @Shadow @Final @Mutable private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void leapfrog$addFuturePack(RepositorySource[] args, CallbackInfo ci) {
        if (sources.stream().noneMatch(s -> s instanceof ClientPackSource)) return;
        sources = ImmutableSet.<RepositorySource>builder().addAll(sources).add(new FuturePack()).build();
    }
}
