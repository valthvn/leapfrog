package dev.leapfrog.mixin;

import dev.leapfrog.geyser.GeyserLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Shadow
    @Final
    Level level;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void leapfrog$trackPotentSulfur(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        if (this.level.isClientSide()) GeyserLogic.onBlockChanged(pos, state);
    }
}
