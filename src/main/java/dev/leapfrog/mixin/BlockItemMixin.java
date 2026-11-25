package dev.leapfrog.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.leapfrog.Config;
import dev.leapfrog.Features;
import dev.leapfrog.block.FutureBlock;
import dev.leapfrog.block.FutureBlocks;
import dev.leapfrog.item.FutureItems;
import dev.leapfrog.sound.FutureSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Placing a 26.x item held as a ViaBackwards stand-in. The client used to predict the stand-in block
 * (quartz stairs for wool stairs, a carpet for a cushion) until the server's answer replaced it. It now
 * predicts the real block, or nothing for a cushion since the server spawns an entity. The place sound
 * is the real one too.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void leapfrog$cushionPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = context.getLevel();
        if (!level.isClientSide()) return;
        Identifier future = FutureItems.originalId(context.getItemInHand());
        if (future == null || !future.getPath().endsWith("_cushion")) return;
        Player player = context.getPlayer();
        if (Config.placeSounds) level.playSound(player, context.getClickedPos(), FutureSounds.CUSHION_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    @WrapOperation(method = "place",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private @Nullable BlockState leapfrog$futureBlockPrediction(BlockItem item, BlockPlaceContext context, Operation<BlockState> original) {
        BlockState standIn = original.call(item, context);
        if (standIn == null || !context.getLevel().isClientSide() || !Config.restoreBlocks || !Features.connectedAs26()) return standIn;
        Identifier future = FutureItems.originalId(context.getItemInHand());
        FutureBlock block = future == null ? null : FutureBlocks.all().get(future.getPath());
        return block == null ? standIn : FutureBlock.matching(block, standIn);
    }

    @WrapOperation(method = "place",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private void leapfrog$futurePlaceSound(Level level, Entity entity, BlockPos pos, SoundEvent sound,
                                                  SoundSource source, float volume, float pitch, Operation<Void> original,
                                                  @Local(argsOnly = true) BlockPlaceContext context) {
        if (level.isClientSide() && Config.placeSounds) {
            Identifier future = FutureItems.originalId(context.getItemInHand());
            if (future != null && future.getPath().endsWith("_cushion")) {
                original.call(level, entity, pos, FutureSounds.CUSHION_PLACE, source, 1.0F, 1.0F);
                return;
            }
            SoundType type = future == null ? null : FutureSounds.forBlock(future);
            if (type != null) {
                original.call(level, entity, pos, type.getPlaceSound(), source, (type.getVolume() + 1.0F) / 2.0F, type.getPitch() * 0.8F);
                return;
            }
        }
        original.call(level, entity, pos, sound, source, volume, pitch);
    }
}
