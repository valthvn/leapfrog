package dev.leapfrog.sulfur;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baby flag and fuse of sulfur cubes, which ViaBackwards drops when it turns them into slimes.
 * Filled by ViaBridge on the network thread, keyed by entity id.
 */
public final class SulfurCubeData {
    private static final Map<Integer, Info> DATA = new ConcurrentHashMap<>();

    private SulfurCubeData() {}

    public static final class Info {
        volatile boolean baby;
        volatile int fuse = -1;
        volatile long fuseSetAt;
    }

    private static Info info(int entityId) {
        return DATA.computeIfAbsent(entityId, id -> new Info());
    }

    public static void setBaby(int entityId, boolean baby) {
        info(entityId).baby = baby;
    }

    public static void setFuse(int entityId, int fuse) {
        Info i = info(entityId);
        i.fuse = fuse;
        i.fuseSetAt = System.currentTimeMillis();
    }

    public static boolean isBaby(int entityId) {
        Info i = DATA.get(entityId);
        return i != null && i.baby;
    }

    /** Remaining fuse in ticks, 0 when not primed. */
    public static float fuseRemaining(int entityId) {
        Info i = DATA.get(entityId);
        if (i == null || i.fuse < 0) return 0.0F;
        float elapsed = (System.currentTimeMillis() - i.fuseSetAt) / 50.0F;
        return Math.max(i.fuse - elapsed + 1.0F, 0.0F);
    }

    public static void remove(int entityId) {
        DATA.remove(entityId);
    }

    public static void clear() {
        DATA.clear();
    }
}
