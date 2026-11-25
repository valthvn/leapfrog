package dev.leapfrog.geyser;

import dev.leapfrog.Config;
import dev.leapfrog.block.FutureBlocks;
import dev.leapfrog.block.StringProperty;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * Client side of the 26.3 potent sulfur geyser. 1.21.11 has no block entity for it, so positions are
 * tracked here and the 26.3 client tickers are run by hand. Needs restored blocks (26.3 connection).
 */
public final class GeyserLogic {
    private static final SoundEvent NOXIOUS_GAS = sound("block.potent_sulfur.noxious_gas");
    private static final SoundEvent ERUPTION_ACTIVE = sound("block.potent_sulfur.geyser_eruption_active");
    private static final SoundEvent CONTINUOUS_ACTIVE = sound("block.potent_sulfur.geyser_continuous_eruption_active");
    private static final double TICK_RANGE = 96.0;

    private static final LongSet POSITIONS = new LongOpenHashSet();
    private static final Long2LongOpenHashMap ERUPTION_TICK = new Long2LongOpenHashMap();
    private static @Nullable Block potentSulfur;
    private static @Nullable StringProperty stateProperty;

    private GeyserLogic() {}

    private static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(path));
    }

    private static boolean init() {
        if (potentSulfur == null) {
            var block = FutureBlocks.all().get("potent_sulfur");
            if (block == null) return false;
            potentSulfur = block;
            for (StringProperty p : block.info().properties()) if (p.getName().equals("potent_sulfur_state")) stateProperty = p;
        }
        return stateProperty != null;
    }

    public static boolean isPotentSulfur(BlockState state) {
        return init() && state.is(potentSulfur);
    }

    private static String stateOf(BlockState state) {
        return state.getValue(stateProperty);
    }

    public static void onChunkLoad(LevelChunk chunk) {
        if (!init()) return;
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> s.is(potentSulfur))) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        if (section.getBlockState(x, y, z).is(potentSulfur)) {
                            POSITIONS.add(BlockPos.asLong(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z));
                        }
        }
    }

    public static void onChunkUnload(LevelChunk chunk) {
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        POSITIONS.removeIf((long p) -> SectionPos.blockToSectionCoord(BlockPos.getX(p)) == cx && SectionPos.blockToSectionCoord(BlockPos.getZ(p)) == cz);
    }

    public static void onBlockChanged(BlockPos pos, BlockState state) {
        if (!init()) return;
        if (state.is(potentSulfur)) POSITIONS.add(pos.asLong());
        else if (POSITIONS.remove(pos.asLong())) ERUPTION_TICK.remove(pos.asLong());
    }

    /** The server sends a block event when an eruption starts. */
    public static void onBlockEvent(ClientLevel level, BlockPos pos) {
        if (isPotentSulfur(level.getBlockState(pos))) ERUPTION_TICK.put(pos.asLong(), level.getGameTime());
    }

    public static void clear() {
        POSITIONS.clear();
        ERUPTION_TICK.clear();
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || POSITIONS.isEmpty() || mc.isPaused() || !Config.geyserEffects) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (var it = POSITIONS.iterator(); it.hasNext(); ) {
            long packed = it.nextLong();
            pos.set(packed);
            if (pos.distToCenterSqr(player.position()) > TICK_RANGE * TICK_RANGE) continue;
            BlockState state = level.getBlockState(pos);
            if (!isPotentSulfur(state)) continue;
            switch (stateOf(state)) {
                case "wet", "dormant" -> noxiousGasTicker(level, pos);
                case "erupting" -> {
                    plumeTicker(level, pos, packed, ERUPTION_ACTIVE);
                    launchTicker(level, pos, player);
                }
                case "continuous" -> {
                    plumeTicker(level, pos, packed, CONTINUOUS_ACTIVE);
                    launchTicker(level, pos, player);
                }
                default -> {
                }
            }
        }
    }

    private static void noxiousGasTicker(ClientLevel level, BlockPos pos) {
        if (level.getGameTime() % 20L != 0L) return;
        BlockPos source = findSource(level, pos);
        if (source != null) {
            Vec3 c = Vec3.atCenterOf(source);
            level.addParticle(GeyserParticleTypes.NOXIOUS_GAS_CLOUD, c.x, c.y, c.z, 0.0, 0.0, 0.0);
        }
    }

    private static void plumeTicker(ClientLevel level, BlockPos pos, long packed, SoundEvent sound) {
        BlockPos source = findSource(level, pos);
        if (source == null) return;
        long eruptionTime = level.getGameTime() - ERUPTION_TICK.getOrDefault(packed, 0L);
        if (eruptionTime % 20L == 0L) {
            int waterBlocks = source.getY() - pos.getY() - 1;
            level.addParticle(new GeyserParticleTypes.Geyser(GeyserParticleTypes.GEYSER, waterBlocks),
                    source.getX() + 0.5, source.getY(), source.getZ() + 0.5, 0.0, 0.0, 0.0);
        }
        if (eruptionTime % 40L == 0L) {
            level.playLocalSound(source.getX() + 0.5, source.getY() + 0.5, source.getZ() + 0.5, sound, SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
    }

    /** Only entities we simulate (the local player) get pushed, same as a 26.3 client. */
    private static void launchTicker(ClientLevel level, BlockPos pos, LocalPlayer player) {
        if (!Config.geyserLaunchActive()) return;
        BlockPos source = findSource(level, pos);
        if (source == null) return;
        int waterBlocks = source.getY() - pos.getY() - 1;
        int height = unobstructedBlockCount(level, pos.above(), waterBlocks);
        AABB area = new AABB(pos.above()).expandTowards(0.0, height - 1, 0.0);
        Entity target = player.isPassenger() ? null : player;
        if (target == null || target.isSpectator() || !target.isAlive() || !target.getBoundingBox().intersects(area)) return;
        Vec3 v = target.getDeltaMovement();
        if (v.y > -0.5 && target.fallDistance > 1.0) target.fallDistance = 1.0;
        if (!player.getAbilities().flying && v.y < 0.3F + waterBlocks * 0.1) {
            target.addDeltaMovement(new Vec3(0.0, 0.2F, 0.0));
        }
    }

    /** Bubbles and gas sound while under water, from PotentSulfurBlock.animateTick. */
    public static void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!Config.geyserEffects || !isPotentSulfur(state) || "dry".equals(stateOf(state))) return;
        if (level.getFluidState(pos.above()).isSourceOfType(Fluids.WATER)) {
            bubble(level, random, pos.getX(), pos.getY() + 1, pos.getZ());
            bubble(level, random, pos.getX(), pos.getY() + 1, pos.getZ());
            if (random.nextInt(10) == 0) {
                level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), NOXIOUS_GAS, SoundSource.AMBIENT, 1.0F, 1.0F, false);
            }
        }
    }

    private static void bubble(Level level, RandomSource random, double x, double y, double z) {
        level.addAlwaysVisibleParticle(GeyserParticleTypes.SULFUR_BUBBLES, x + random.nextFloat(), y + random.nextFloat(), z + random.nextFloat(), 0.0, 0.0, 0.0);
    }

    private static CollisionContext positionContext(int y) {
        return CollisionContext.withPosition(null, y);
    }

    private static boolean passable(BlockState state, Level level, BlockPos pos, CollisionContext ctx) {
        return state.isAir() || state.is(Blocks.WATER) || state.getCollisionShape(level, pos, ctx).isEmpty();
    }

    private static int unobstructedBlockCount(Level level, BlockPos pos, int waterBlocks) {
        int max = 6 * waterBlocks;
        CollisionContext ctx = positionContext(pos.below().getY());
        for (int i = 0; i < max; i++) {
            BlockPos p = pos.above(i);
            if (!passable(level.getBlockState(p), level, p, ctx)) return i;
        }
        return max;
    }

    /** Topmost free block above the water column (at most 4 water blocks), or null. */
    static @Nullable BlockPos findSource(Level level, BlockPos origin) {
        int maxY = origin.getY() + 4 + 1;
        CollisionContext ctx = positionContext(origin.getY());
        BlockPos.MutableBlockPos pos = origin.above(1).mutable();
        while (pos.getY() <= maxY) {
            BlockState state = level.getBlockState(pos);
            boolean waterSource = level.getFluidState(pos).isSourceOfType(Fluids.WATER);
            if (!waterSource || !state.is(Blocks.WATER) && !passable(state, level, pos, ctx)) {
                if (state.isAir() || passable(state, level, pos, ctx)) return pos.immutable();
                break;
            }
            pos.move(Direction.UP);
        }
        return null;
    }

    static boolean canBeReachedByNoxiousGas(Level level, BlockPos source, Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos);
        if (!passable(level.getBlockState(blockPos), level, blockPos, positionContext(blockPos.below().getY()))) return false;
        if (pos.distanceToSqr(Vec3.atCenterOf(source)) > 9.0) return false;
        Vec3 belowSource = Vec3.atCenterOf(source.below());
        Vec3 belowPos = new Vec3(pos.x, pos.y - 1.0, pos.z);
        return level.getFluidState(BlockPos.containing(belowPos)).isSourceOfType(Fluids.WATER)
                && level.clip(new ClipContext(belowSource, belowPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() != HitResult.Type.BLOCK;
    }
}
