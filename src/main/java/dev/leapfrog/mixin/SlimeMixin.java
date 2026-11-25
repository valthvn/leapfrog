package dev.leapfrog.mixin;

import dev.leapfrog.Config;
import dev.leapfrog.geyser.GeyserParticleTypes;
import dev.leapfrog.sulfur.SulfurCubeRenderer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slime.class)
abstract class SlimeMixin {
    @Inject(method = "getParticleType", at = @At("HEAD"), cancellable = true)
    private void leapfrog$sulfurGoo(CallbackInfoReturnable<ParticleOptions> cir) {
        if (Config.sulfurCube && SulfurCubeRenderer.isSulfurCube((Slime) (Object) this)) {
            cir.setReturnValue(GeyserParticleTypes.SULFUR_CUBE_GOO);
        }
    }
}
