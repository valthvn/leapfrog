package dev.leapfrog.via;

import com.viaversion.viabackwards.protocol.v26_1to1_21_11.Protocol26_1To1_21_11;
import com.viaversion.viabackwards.protocol.v26_2to26_1.Protocol26_2To26_1;
import com.viaversion.viabackwards.protocol.v26_3to26_2.Protocol26_3To26_2;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.data.Mappings;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes26_3;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.ProtocolManager;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMapping;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMappings;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandler;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.VersionedTypes;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import dev.leapfrog.Leapfrog;
import dev.leapfrog.cushion.CushionColors;
import dev.leapfrog.sulfur.SulfurCubeData;
import java.lang.reflect.Field;

/** Hooks into the client ViaBackwards protocols. Only load this class when ViaBackwards is present. */
public final class ViaBridge {
    private static volatile boolean installed;

    private ViaBridge() {}

    private static volatile boolean hooked;
    private static volatile long lastActive;

    public static boolean isInstalled() {
        return installed;
    }

    public static boolean isHooked() {
        return hooked;
    }

    /** Runs for every restored block of every chunk, so it only sets a flag. */
    static void markActive() {
        if (!active) active = true;
    }

    private static volatile boolean active;

    /** A restored block went through recently, which means we're connected as 26.3. */
    public static boolean recentlyActive() {
        return System.currentTimeMillis() - lastActive < 5 * 60_000L;
    }

    public static void tick() {
        if (active) {
            active = false;
            lastActive = System.currentTimeMillis();
        }
    }

    /** Safe to call repeatedly. Does nothing until ViaVersion has loaded its mappings. */
    public static synchronized void tryInstall() {
        if (installed) return;
        try {
            if (!Via.isLoaded()) return;
            ProtocolManager pm = Via.getManager().getProtocolManager();
            Protocol<?, ?, ?, ?> entry = pm.getProtocol(Protocol26_3To26_2.class);
            Protocol<?, ?, ?, ?> pass = pm.getProtocol(Protocol26_2To26_1.class);
            Protocol<?, ?, ?, ?> exit = pm.getProtocol(Protocol26_1To1_21_11.class);
            if (entry == null || pass == null || exit == null) {
                Leapfrog.LOG.warn("ViaBackwards does not provide the 26.3 -> 1.21.11 chain; blocks will not be restored");
                installed = true;
                return;
            }
            if (!loaded(entry) || !loaded(pass) || !loaded(exit)) return;
            wrap(entry, FutureBlockMappings.Stage.ENTRY);
            wrap(pass, FutureBlockMappings.Stage.PASS);
            wrap(exit, FutureBlockMappings.Stage.EXIT);
            wrapSounds(entry, FutureBlockMappings.Stage.ENTRY);
            wrapSounds(pass, FutureBlockMappings.Stage.PASS);
            wrapSounds(exit, FutureBlockMappings.Stage.EXIT);
            captureCushionColors(entry);
            installed = true;
            hooked = true;
            Leapfrog.LOG.info("Hooked ViaBackwards: 26.x blocks will be restored");
        } catch (Throwable t) {
            installed = true;
            Leapfrog.LOG.error("Could not hook ViaBackwards", t);
        }
    }

    private static void wrapSounds(Protocol<?, ?, ?, ?> protocol, FutureBlockMappings.Stage stage) throws ReflectiveOperationException {
        MappingData data = protocol.getMappingData();
        Mappings current = data.getSoundMappings();
        if (current == null || current instanceof FutureSoundMappings) return;
        Field field = findField(data.getClass(), "soundMappings");
        field.setAccessible(true);
        field.set(data, FutureSoundMappings.wrap(current, stage));
    }

    /**
     * ViaBackwards drops the cushion colour and the sulfur cube data, so read them from the 26.3
     * entity data packet before it runs.
     */
    private static void captureCushionColors(Protocol<?, ?, ?, ?> protocol) throws ReflectiveOperationException {
        Field f = findField(protocol.getClass(), "clientboundMappings");
        f.setAccessible(true);
        PacketMappings mappings =
                (PacketMappings) f.get(protocol);
        var packet = ClientboundPackets26_3.SET_ENTITY_DATA;
        PacketMapping mapping = mappings.mappedPacket(packet.state(), packet.getId());
        if (mapping == null) return;
        PacketHandler original = mapping.handler();
        mapping.setHandler(wrapper -> {
            try {
                // These packets are frequent, only decode the ones for cushions and sulfur cubes.
                int entityId = wrapper.passthrough(Types.VAR_INT);
                var tracked = protocol.getEntityRewriter().tracker(wrapper.user()).entity(entityId);
                var type = tracked == null ? null : tracked.entityType();
                boolean cushion = type == EntityTypes26_3.CUSHION;
                boolean sulfurCube = type == EntityTypes26_3.SULFUR_CUBE;
                if (!cushion && !sulfurCube) {
                    wrapper.rewindReader(1);
                } else {
                    var data = wrapper.passthrough(VersionedTypes.V26_3.entityDataList);
                    wrapper.rewindReader(2);
                    if (cushion) {
                        for (var d : data) {
                            if (d.id() == 8 && d.value() instanceof Integer color) CushionColors.put(entityId, color);
                        }
                    } else {
                        // 16 is the baby flag, 19 the fuse (primed when >= 0).
                        for (var d : data) {
                            if (d.id() == 16 && d.value() instanceof Boolean baby) SulfurCubeData.setBaby(entityId, baby);
                            if (d.id() == 19 && d.value() instanceof Integer fuse) SulfurCubeData.setFuse(entityId, fuse);
                        }
                    }
                }
            } catch (Exception e) {
                Leapfrog.LOG.debug("Entity data capture failed", e);
            }
            if (original != null) original.handle(wrapper);
        });
    }

    private static boolean loaded(Protocol<?, ?, ?, ?> protocol) {
        MappingData data = protocol.getMappingData();
        return data != null && data.getBlockStateMappings() != null;
    }

    private static void wrap(Protocol<?, ?, ?, ?> protocol, FutureBlockMappings.Stage stage) throws ReflectiveOperationException {
        MappingData data = protocol.getMappingData();
        Mappings current = data.getBlockStateMappings();
        if (current instanceof FutureBlockMappings) return;
        Field field = findField(data.getClass(), "blockStateMappings");
        field.setAccessible(true);
        field.set(data, new FutureBlockMappings(current, stage));
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
