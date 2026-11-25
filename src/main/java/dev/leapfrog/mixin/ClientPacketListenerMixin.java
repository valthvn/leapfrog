package dev.leapfrog.mixin;

import dev.leapfrog.sound.CushionSounds;
import net.minecraft.client.multiplayer.ClientPacketListener;
import dev.leapfrog.geyser.GeyserLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSoundEvent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void leapfrog$cushionSounds(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (CushionSounds.intercept(packet)) ci.cancel();
    }

    @Inject(method = "handleBlockEvent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER))
    private void leapfrog$geyserEruption(ClientboundBlockEventPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().level != null) GeyserLogic.onBlockEvent(Minecraft.getInstance().level, packet.getPos());
    }
}
