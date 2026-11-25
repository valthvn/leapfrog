package dev.leapfrog.geyser;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.leapfrog.Leapfrog;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/** Geyser and potent sulfur particle types, registered under {@code leapfrog:}. */
public final class GeyserParticleTypes {
    public static final SimpleParticleType SULFUR_BUBBLES = FabricParticleTypes.simple(false);
    public static final SimpleParticleType NOXIOUS_GAS = FabricParticleTypes.simple(false);
    public static final SimpleParticleType NOXIOUS_GAS_CLOUD = FabricParticleTypes.simple(false);
    /** Splashed by a landing sulfur cube instead of slime balls. */
    public static final SimpleParticleType SULFUR_CUBE_GOO = FabricParticleTypes.simple(false);
    public static final ParticleType<Geyser> GEYSER = FabricParticleTypes.complex(true, Geyser::codec, Geyser::streamCodec);
    public static final ParticleType<Geyser> GEYSER_PLUME = FabricParticleTypes.complex(true, Geyser::codec, Geyser::streamCodec);
    public static final ParticleType<GeyserBase> GEYSER_BASE = FabricParticleTypes.complex(true, GeyserBase::codec, GeyserBase::streamCodec);
    public static final ParticleType<GeyserBase> GEYSER_POOF = FabricParticleTypes.complex(true, GeyserBase::codec, GeyserBase::streamCodec);

    private GeyserParticleTypes() {}

    public static void register() {
        reg("sulfur_bubbles", SULFUR_BUBBLES);
        reg("noxious_gas", NOXIOUS_GAS);
        reg("noxious_gas_cloud", NOXIOUS_GAS_CLOUD);
        reg("geyser", GEYSER);
        reg("geyser_plume", GEYSER_PLUME);
        reg("geyser_base", GEYSER_BASE);
        reg("geyser_poof", GEYSER_POOF);
        reg("sulfur_cube_goo", SULFUR_CUBE_GOO);
    }

    private static void reg(String name, ParticleType<?> type) {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(Leapfrog.MOD_ID, name), type);
    }

    /** Same as 26.3 GeyserParticleOptions. */
    public record Geyser(ParticleType<Geyser> type, int waterBlocks) implements ParticleOptions {
        public static MapCodec<Geyser> codec(ParticleType<Geyser> type) {
            return RecordCodecBuilder.mapCodec(i -> i.group(ExtraCodecs.POSITIVE_INT.fieldOf("water_blocks").forGetter(Geyser::waterBlocks))
                    .apply(i, w -> new Geyser(type, w)));
        }

        public static StreamCodec<? super ByteBuf, Geyser> streamCodec(ParticleType<Geyser> type) {
            return StreamCodec.composite(ByteBufCodecs.INT, Geyser::waterBlocks, w -> new Geyser(type, w));
        }

        @Override
        public ParticleType<Geyser> getType() {
            return type;
        }
    }

    /** Same as 26.3 GeyserBaseParticleOptions. */
    public record GeyserBase(ParticleType<GeyserBase> type, int waterBlocks, float burstImpulseBase) implements ParticleOptions {
        public static MapCodec<GeyserBase> codec(ParticleType<GeyserBase> type) {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                            ExtraCodecs.POSITIVE_INT.fieldOf("water_blocks").forGetter(GeyserBase::waterBlocks),
                            Codec.FLOAT.fieldOf("burst_impulse_base").forGetter(GeyserBase::burstImpulseBase))
                    .apply(i, (w, b) -> new GeyserBase(type, w, b)));
        }

        public static StreamCodec<? super ByteBuf, GeyserBase> streamCodec(ParticleType<GeyserBase> type) {
            return StreamCodec.composite(ByteBufCodecs.INT, GeyserBase::waterBlocks, ByteBufCodecs.FLOAT, GeyserBase::burstImpulseBase,
                    (w, b) -> new GeyserBase(type, w, b));
        }

        @Override
        public ParticleType<GeyserBase> getType() {
            return type;
        }
    }
}
