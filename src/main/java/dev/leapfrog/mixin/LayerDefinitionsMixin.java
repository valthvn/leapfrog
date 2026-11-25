package dev.leapfrog.mixin;

import dev.leapfrog.baby.BabyModels;
import java.util.Map;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LayerDefinitions.class)
public abstract class LayerDefinitionsMixin {
    @Inject(method = "createRoots", at = @At("RETURN"), cancellable = true)
    private static void leapfrog$babyLayers(CallbackInfoReturnable<Map<ModelLayerLocation, LayerDefinition>> cir) {
        cir.setReturnValue(BabyModels.patch(cir.getReturnValue()));
    }
}
