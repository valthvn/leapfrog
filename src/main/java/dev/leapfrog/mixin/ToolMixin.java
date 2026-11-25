package dev.leapfrog.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.leapfrog.block.FutureBlock;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tool rules check block tags, which don't contain the 26.x blocks. Use the 26.3 tags for those. */
@Mixin(Tool.class)
abstract class ToolMixin {
    @WrapOperation(method = {"getMiningSpeed", "isCorrectForDrops"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/core/HolderSet;)Z"))
    private boolean leapfrog$futureTags(BlockState state, HolderSet<Block> set, Operation<Boolean> original) {
        if (state.getBlock() instanceof FutureBlock block) return block.inTag(set);
        return original.call(state, set);
    }
}
