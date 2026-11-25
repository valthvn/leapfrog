package dev.leapfrog.sound;

import dev.leapfrog.Config;
import dev.leapfrog.cushion.CushionDetector;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * ViaBackwards turns cushion sounds into wool place/break sounds. Wool blocks play them at the block
 * centre, cushions play them where the cushion sits, so a wool sound away from a block centre is held
 * for a few ticks. If a cushion spawned, got ridden or vanished there, the cushion sound plays
 * instead, otherwise the wool sound is played as it was.
 */
public final class CushionSounds {
    private static final Identifier WOOL_PLACE = Identifier.withDefaultNamespace("block.wool.place");
    private static final Identifier WOOL_BREAK = Identifier.withDefaultNamespace("block.wool.break");
    private static final int RESOLVE_DELAY_TICKS = 3;
    private static final int YOUNG_CUSHION_TICKS = 10;
    private static final int REMOVED_MEMORY_TICKS = 40;
    private static final double MATCH_DISTANCE = 0.75;

    private record Pending(boolean placeLike, Vec3 pos, SoundEvent original, SoundSource source,
                           float volume, float pitch, long dueTick) {}

    private record Removed(Vec3 pos, long tick) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final ArrayDeque<Removed> REMOVED = new ArrayDeque<>();
    private static long tick;

    private CushionSounds() {}

    /** Returns true when the packet is handled here. Render thread. */
    public static boolean intercept(ClientboundSoundPacket packet) {
        if (!Config.cushionSounds) return false;
        Identifier id = packet.getSound().value().location();
        boolean place = id.equals(WOOL_PLACE);
        if (!place && !id.equals(WOOL_BREAK)) return false;
        double y = packet.getY();
        Vec3 pos = new Vec3(packet.getX(), y, packet.getZ());
        // Cushions on slabs or carpets sit above an integer Y, so also check for a cushion right there.
        boolean onTopOfBlock = Math.abs(y - Math.rint(y)) <= 0.13;
        if (!onTopOfBlock && !cushionLevelWith(pos)) return false;
        PENDING.add(new Pending(place, pos, packet.getSound().value(),
                packet.getSource(), packet.getVolume(), packet.getPitch(), tick + RESOLVE_DELAY_TICKS));
        return true;
    }

    /** Whether a cushion is (or just was) at the height of this sound. */
    private static boolean cushionLevelWith(Vec3 pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        AABB box = new AABB(pos.x - MATCH_DISTANCE, pos.y - 0.1, pos.z - MATCH_DISTANCE, pos.x + MATCH_DISTANCE, pos.y + 0.1, pos.z + MATCH_DISTANCE);
        if (!level.getEntitiesOfClass(FallingBlockEntity.class, box, e -> CushionDetector.cushionColor(e) != null).isEmpty()) return true;
        for (Removed r : REMOVED) if (box.contains(r.pos())) return true;
        return false;
    }

    /** Remembers where cushions disappear, so a following wool break sound can be matched. */
    public static void onEntityUnload(Entity entity) {
        if (CushionDetector.cushionColor(entity) != null) {
            REMOVED.addLast(new Removed(entity.position(), tick));
        }
    }

    public static void tick(Minecraft mc) {
        tick++;
        while (!REMOVED.isEmpty() && tick - REMOVED.peekFirst().tick() > REMOVED_MEMORY_TICKS) REMOVED.removeFirst();
        ClientLevel level = mc.level;
        if (level == null) {
            PENDING.clear();
            REMOVED.clear();
            return;
        }
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.dueTick() > tick) continue;
            it.remove();
            level.playLocalSound(p.pos().x, p.pos().y, p.pos().z, resolve(level, p), p.source(), p.volume(), p.pitch(), false);
        }
    }

    private static SoundEvent resolve(ClientLevel level, Pending p) {
        FallingBlockEntity cushion = cushionAt(level, p.pos());
        if (p.placeLike()) {
            if (cushion == null) return p.original();
            return cushion.tickCount <= YOUNG_CUSHION_TICKS + RESOLVE_DELAY_TICKS ? FutureSounds.CUSHION_PLACE : FutureSounds.CUSHION_SIT;
        }
        if (cushion != null) return FutureSounds.CUSHION_GET_UP;
        for (Removed r : REMOVED) {
            if (r.pos().distanceToSqr(p.pos()) < MATCH_DISTANCE * MATCH_DISTANCE) return FutureSounds.CUSHION_BREAK;
        }
        return p.original();
    }

    private static @Nullable FallingBlockEntity cushionAt(ClientLevel level, Vec3 pos) {
        AABB box = AABB.ofSize(pos, MATCH_DISTANCE * 2, MATCH_DISTANCE * 2, MATCH_DISTANCE * 2);
        FallingBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (FallingBlockEntity e : level.getEntitiesOfClass(FallingBlockEntity.class, box, e -> CushionDetector.cushionColor(e) != null)) {
            double d = e.position().distanceToSqr(pos);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }
}
