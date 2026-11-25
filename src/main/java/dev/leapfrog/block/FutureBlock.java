package dev.leapfrog.block;

import dev.leapfrog.geyser.GeyserLogic;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Client copy of a block that only exists in 26.x. Shapes, light and properties come from the data
 * dumped out of 26.3, the look from its blockstate and model files.
 */
public class FutureBlock extends Block {
    // 1.21.11 shears look at #leaves and #wool, 26.3 shears have their own tags.
    private static final Map<Identifier, Identifier> SHEARS_ALIASES = Map.of(
            Identifier.withDefaultNamespace("leaves"), Identifier.withDefaultNamespace("shears_extreme_breaking_speed"),
            Identifier.withDefaultNamespace("wool"), Identifier.withDefaultNamespace("shears_major_breaking_speed"));

    // Block's constructor calls createBlockStateDefinition before our fields are set.
    private static final ThreadLocal<FutureBlockData.BlockInfo> CONSTRUCTING = new ThreadLocal<>();

    private final FutureBlockData.BlockInfo info;
    private final Map<BlockState, FutureBlockData.StateInfo> stateInfo = new IdentityHashMap<>();

    public static FutureBlock create(FutureBlockData.BlockInfo info, Properties properties) {
        CONSTRUCTING.set(info);
        try {
            return new FutureBlock(info, properties);
        } finally {
            CONSTRUCTING.remove();
        }
    }

    private FutureBlock(FutureBlockData.BlockInfo info, Properties properties) {
        super(properties);
        this.info = info;

        BlockState def = this.stateDefinition.any();
        for (StringProperty p : info.properties()) def = def.setValue(p, info.defaults().get(p.getName()));
        this.registerDefaultState(def);
        for (BlockState state : this.stateDefinition.getPossibleStates()) {
            FutureBlockData.StateInfo si = info.state(key(state, info));
            if (si != null) stateInfo.put(state, si);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (StringProperty p : CONSTRUCTING.get().properties()) builder.add(p);
    }

    /** Properties in 26.3 order, for example {@code facing=north,half=top,waterlogged=false}. */
    public static String key(BlockState state, FutureBlockData.BlockInfo info) {
        StringBuilder sb = new StringBuilder();
        for (StringProperty p : info.properties()) {
            if (!sb.isEmpty()) sb.append(',');
            sb.append(p.getName()).append('=').append(state.getValue(p));
        }
        return sb.toString();
    }

    /** State of {@code block} taking the values of the stand-in's same-named properties (facing, half, axis...). */
    public static BlockState matching(FutureBlock block, BlockState standIn) {
        BlockState out = block.defaultBlockState();
        for (Property<?> p : standIn.getProperties()) {
            for (StringProperty target : block.info.properties()) {
                if (target.getName().equals(p.getName())) out = out.trySetValue(target, valueName(standIn, p));
            }
        }
        return out;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    public FutureBlockData.BlockInfo info() {
        return info;
    }

    /** Tag check against the 26.3 tags, which the server never sends for these blocks. */
    public boolean inTag(HolderSet<Block> set) {
        Optional<TagKey<Block>> key = set.unwrapKey();
        FutureMining.Mining mining = FutureMining.get(info.name());
        if (key.isEmpty() || mining == null) return false;
        Identifier tag = key.get().location();
        if (mining.tags().contains(tag)) return true;
        Identifier alias = SHEARS_ALIASES.get(tag);
        return alias != null && mining.tags().contains(alias);
    }

    public static int lightEmission(BlockState state) {
        FutureBlockData.BlockInfo info = CONSTRUCTING.get();
        if (info == null && state.getBlock() instanceof FutureBlock fb) info = fb.info;
        if (info == null) return 0;
        FutureBlockData.StateInfo si = info.state(key(state, info));
        return si == null ? 0 : si.lightEmission();
    }

    private FutureBlockData.BlockInfo blockInfo() {
        return info != null ? info : CONSTRUCTING.get();
    }

    private FutureBlockData.StateInfo data(BlockState state) {
        if (stateInfo != null) {
            FutureBlockData.StateInfo si = stateInfo.get(state);
            if (si != null) return si;
        }
        FutureBlockData.BlockInfo i = blockInfo();
        return i.state(key(state, i));
    }

    private VoxelShape offset(VoxelShape shape, BlockState state, BlockPos pos) {
        if (!blockInfo().offset()) return shape;
        Vec3 o = state.getOffset(pos);
        return shape.move(o.x, o.y, o.z);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return offset(data(state).outline(), state, pos);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return offset(data(state).collision(), state, pos);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return data(state).occlusion();
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return data(state).useShapeForLightOcclusion();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return data(state).propagatesSkylight();
    }

    @Override
    protected int getLightBlock(BlockState state) {
        return data(state).lightDampening();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        GeyserLogic.animateTick(state, level, pos, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        for (StringProperty p : blockInfo().properties()) {
            if (p.getName().equals("waterlogged") && "true".equals(state.getValue(p))) return Fluids.WATER.getSource(false);
        }
        return super.getFluidState(state);
    }
}
