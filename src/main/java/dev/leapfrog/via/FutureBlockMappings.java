package dev.leapfrog.via;

import com.viaversion.viaversion.api.data.Mappings;
import dev.leapfrog.Config;
import dev.leapfrog.block.FutureBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wraps a ViaBackwards block state mapping so 26.x blocks make it through 26.3 to 1.21.11 instead of
 * being replaced by old blocks. The first step turns a 26.3 id into {@code SENTINEL + id}, the middle
 * steps leave sentinels alone and the last step maps them to our own block states.
 *
 * <p>The reported size is raised so chunk palettes get enough bits for the sentinels and for the
 * bigger client registry. The client reads the bit count from the packet, so that's fine.
 */
public final class FutureBlockMappings implements Mappings {
    public enum Stage { ENTRY, PASS, EXIT }

    public static final int SENTINEL = 1 << 16;
    private static final int WIDE_SIZE = 1 << 17;

    private final Mappings delegate;
    private final Stage stage;

    public FutureBlockMappings(Mappings delegate, Stage stage) {
        this.delegate = delegate;
        this.stage = stage;
    }

    public Mappings delegate() {
        return delegate;
    }

    @Override
    public int getNewId(int id) {
        switch (stage) {
            case ENTRY:
                if (Config.restoreBlocks && FutureBlocks.isRestored(id)) {
                    ViaBridge.markActive();
                    return SENTINEL + id;
                }
                return delegate.getNewId(id);
            case PASS:
                if (id >= SENTINEL) return id;
                return delegate.getNewId(id);
            default:
                if (id >= SENTINEL) return FutureBlocks.clientStateId(id - SENTINEL);
                return delegate.getNewId(id);
        }
    }

    @Override
    public void setNewId(int id, int mappedId) {
        delegate.setNewId(id, mappedId);
    }

    @Override
    public int size() {
        // ENTRY reads the real 26.3 stream, so it must keep the real size.
        return stage == Stage.ENTRY ? delegate.size() : Math.max(delegate.size(), WIDE_SIZE);
    }

    @Override
    public int mappedSize() {
        if (stage == Stage.EXIT) return Math.max(delegate.mappedSize(), Block.BLOCK_STATE_REGISTRY.size());
        return Math.max(delegate.mappedSize(), WIDE_SIZE);
    }

    @Override
    public Mappings inverse() {
        return delegate.inverse();
    }

    @Override
    public boolean isIdentity() {
        return false;
    }
}
