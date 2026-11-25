package dev.leapfrog.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.leapfrog.Config;
import dev.leapfrog.cushion.CushionDetector;
import dev.leapfrog.cushion.CushionRenderState;
import dev.leapfrog.cushion.CushionRenderer;
import dev.leapfrog.sulfur.SulfurCubeRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Unique
    private CushionRenderer leapfrog$cushionRenderer;
    @Unique
    private SulfurCubeRenderer leapfrog$sulfurRenderer;

    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void leapfrog$createRenderers(ResourceManager resourceManager, CallbackInfo ci,
                                                 @Local EntityRendererProvider.Context context) {
        this.leapfrog$cushionRenderer = new CushionRenderer(context);
        this.leapfrog$sulfurRenderer = new SulfurCubeRenderer(context);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("HEAD"), cancellable = true)
    private void leapfrog$cushionForEntity(Entity entity, CallbackInfoReturnable cir) {
        if (this.leapfrog$cushionRenderer != null && Config.cushionModel && CushionDetector.cushionColor(entity) != null) {
            cir.setReturnValue(this.leapfrog$cushionRenderer);
        } else if (this.leapfrog$sulfurRenderer != null && Config.sulfurCube
                && SulfurCubeRenderer.isSulfurCube(entity)) {
            cir.setReturnValue(this.leapfrog$sulfurRenderer);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("HEAD"), cancellable = true)
    private void leapfrog$cushionForState(EntityRenderState state, CallbackInfoReturnable cir) {
        if (state instanceof CushionRenderState && this.leapfrog$cushionRenderer != null) {
            cir.setReturnValue(this.leapfrog$cushionRenderer);
        } else if (state instanceof SulfurCubeRenderer.State && this.leapfrog$sulfurRenderer != null) {
            cir.setReturnValue(this.leapfrog$sulfurRenderer);
        }
    }
}
