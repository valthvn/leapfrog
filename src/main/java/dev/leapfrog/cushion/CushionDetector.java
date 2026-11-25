package dev.leapfrog.cushion;

import dev.leapfrog.Config;
import java.util.EnumMap;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * ViaBackwards sends cushions as carpet falling blocks with NoGravity. Real falling blocks always fall,
 * so that's enough to spot them. A "Cushion" custom name, which some servers add, also counts.
 */
public final class CushionDetector {
    private static final EnumMap<DyeColor, Identifier> TEXTURES = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : DyeColor.values()) {
            TEXTURES.put(color, Identifier.withDefaultNamespace("textures/entity/cushion/" + color.getName() + "_cushion.png"));
        }
    }

    private CushionDetector() {}

    public static Identifier texture(DyeColor color) {
        return TEXTURES.get(color);
    }

    /** Null when the entity isn't a cushion. */
    public static @Nullable DyeColor cushionColor(Entity entity) {
        if (!(entity instanceof FallingBlockEntity falling)) return null;
        if (!Config.cushionModel && !Config.cushionSeat && !Config.cushionSounds) return null;
        BlockState state = falling.getBlockState();
        if (!(state.getBlock() instanceof WoolCarpetBlock carpet)) return null;
        if (falling.isNoGravity() || hasCushionName(entity)) {
            DyeColor real = CushionColors.get(entity.getId());
            return real != null ? real : carpet.getColor();
        }
        return null;
    }

    private static boolean hasCushionName(Entity entity) {
        Component name = entity.getCustomName();
        return name != null && name.getString().toLowerCase(Locale.ROOT).contains("cushion");
    }
}
