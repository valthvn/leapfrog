package dev.leapfrog.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.leapfrog.item.FutureItems;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @ModifyExpressionValue(
            method = {"appendItemLayers", "shouldPlaySwapAnimation", "swapAnimationScale"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object leapfrog$futureModel(Object original, @Local(argsOnly = true) ItemStack stack) {
        if (original == null || original instanceof Identifier) {
            return FutureItems.remapModel(stack, (Identifier) original);
        }
        return original;
    }
}
