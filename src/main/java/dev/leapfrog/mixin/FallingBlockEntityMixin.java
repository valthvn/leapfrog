package dev.leapfrog.mixin;

import dev.leapfrog.Config;
import dev.leapfrog.cushion.CushionDetector;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoGravity arrives after the spawn packet, so resize once we know it's a cushion. */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void leapfrog$resizeCushion(CallbackInfo ci) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        boolean cushion = Config.cushionSeat && CushionDetector.cushionColor(self) != null;
        if (self.level().isClientSide() && cushion != (self.getBbHeight() == 0.25F)) {
            self.refreshDimensions();
        }
    }
}
