package dev.leapfrog.cushion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/** Cushion colours read from the 26.3 stream, since ViaBackwards sends every cushion as white carpet. */
public final class CushionColors {
    private static final Map<Integer, DyeColor> COLORS = new ConcurrentHashMap<>();

    private CushionColors() {}

    public static void put(int entityId, int dyeColorId) {
        DyeColor color = DyeColor.byId(dyeColorId);
        COLORS.put(entityId, color);
    }

    public static @Nullable DyeColor get(int entityId) {
        return COLORS.get(entityId);
    }

    public static void remove(int entityId) {
        COLORS.remove(entityId);
    }

    public static void clear() {
        COLORS.clear();
    }
}
