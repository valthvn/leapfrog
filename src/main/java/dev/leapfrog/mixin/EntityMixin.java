package dev.leapfrog.mixin;

import dev.leapfrog.Config;
import dev.leapfrog.cushion.CushionDetector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives cushions their real 1 x 0.25 size. The falling block ViaBackwards sends is 0.98 tall, which
 * puts the rider way too high.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Unique
    private static final EntityDimensions LEAPFROG$CUSHION = EntityDimensions.fixed(1.0F, 0.25F);

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void leapfrog$cushionSize(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((Object) this instanceof FallingBlockEntity && Config.cushionSeat && CushionDetector.cushionColor((Entity) (Object) this) != null) {
            cir.setReturnValue(LEAPFROG$CUSHION);
        }
    }
}
