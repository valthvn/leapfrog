package dev.leapfrog.mixin;

import dev.leapfrog.baby.BabyModels;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RenderTypes.class)
public abstract class RenderTypesMixin {
    @ModifyVariable(method = {
            "entitySolid(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            "entityCutout(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            "entityCutoutNoCull(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            "entityCutoutNoCullZOffset(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            "entityTranslucent(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            "entitySmoothCutout(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"
    }, at = @At("HEAD"), argsOnly = true, require = 0)
    private static Identifier leapfrog$babyTexture(Identifier texture) {
        return BabyModels.texture(texture);
    }
}
