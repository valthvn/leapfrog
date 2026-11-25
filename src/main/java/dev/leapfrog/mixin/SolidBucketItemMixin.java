package dev.leapfrog.mixin;

import dev.leapfrog.item.FutureItems;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The sulfur cube bucket arrives as a powder snow bucket. Placing powder snow client-side only sends
 * a block click, which the 26.3 server ignores, so the cube took two clicks. Skip to "use item" like
 * a real mob bucket does.
 */
@Mixin(SolidBucketItem.class)
abstract class SolidBucketItemMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void leapfrog$futureMobBucket(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!context.getLevel().isClientSide()) return;
        Identifier future = FutureItems.originalId(context.getItemInHand());
        if (future != null && future.getPath().endsWith("_bucket")) cir.setReturnValue(InteractionResult.PASS);
    }
}
