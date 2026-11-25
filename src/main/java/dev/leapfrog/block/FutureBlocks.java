package dev.leapfrog.block;

import dev.leapfrog.Leapfrog;
import dev.leapfrog.sound.FutureSounds;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Registers one {@link FutureBlock} per 26.x block and maps 26.3 network state ids to them. */
public final class FutureBlocks {
    private static final Map<String, FutureBlock> BY_NAME = new LinkedHashMap<>();
    private static final Int2ObjectMap<BlockState> BY_FUTURE_ID = new Int2ObjectOpenHashMap<>();

    private FutureBlocks() {}

    public static void register() {
        FutureBlockData data = FutureBlockData.load();
        for (FutureBlockData.BlockInfo info : data.blocks().values()) {
            Identifier id = Identifier.fromNamespaceAndPath(Leapfrog.MOD_ID, info.name());
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .lightLevel(FutureBlock::lightEmission);
            // Same hardness as the server, otherwise the client predicts a different break time.
            FutureMining.Mining mining = FutureMining.get(info.name());
            if (mining != null) {
                props.strength(mining.hardness());
                if (mining.requiresTool()) props.requiresCorrectToolForDrops();
            } else {
                props.strength(1.0F);
            }
            SoundType sound = FutureSounds.forBlock(Identifier.withDefaultNamespace(info.name()));
            if (sound != null) props.sound(sound);
            if (info.states().values().stream().anyMatch(s -> !s.canOcclude())) props.noOcclusion();
            if (info.states().values().stream().allMatch(s -> s.collision().isEmpty())) props.noCollision();
            if (info.offset()) props.offsetType(BlockBehaviour.OffsetType.XZ);
            // Keep the state shape cache unless the collision box moves with the random offset.
            if (info.offset() && info.states().values().stream().anyMatch(s -> !s.collision().isEmpty())) props.dynamicShape();

            FutureBlock block = Registry.register(BuiltInRegistries.BLOCK, id, FutureBlock.create(info, props));
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (Block.BLOCK_STATE_REGISTRY.getId(state) == -1) Block.BLOCK_STATE_REGISTRY.add(state);
                state.initCache();
                FutureBlockData.StateInfo si = info.state(FutureBlock.key(state, info));
                if (si != null) BY_FUTURE_ID.put(si.futureId(), state);
            }
            BY_NAME.put(info.name(), block);
        }
        Leapfrog.LOG.info("Registered {} future blocks ({} states)", BY_NAME.size(), BY_FUTURE_ID.size());
    }

    public static Map<String, FutureBlock> all() {
        return BY_NAME;
    }

    /** Client block state for a 26.3 network block-state id, or null if it is not a restored block. */
    public static @Nullable BlockState byFutureId(int futureStateId) {
        return BY_FUTURE_ID.get(futureStateId);
    }

    public static boolean isRestored(int futureStateId) {
        return BY_FUTURE_ID.containsKey(futureStateId);
    }

    /** Client state id for a 26.3 state id, or 0 (air). Not cached: Fabric registry sync can renumber states. */
    public static int clientStateId(int futureStateId) {
        BlockState state = BY_FUTURE_ID.get(futureStateId);
        return state == null ? 0 : Block.BLOCK_STATE_REGISTRY.getId(state);
    }
}
