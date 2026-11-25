package dev.leapfrog.via;

import com.viaversion.viaversion.api.data.FullMappings;
import com.viaversion.viaversion.api.data.Mappings;
import com.viaversion.viaversion.util.Key;
import dev.leapfrog.Config;
import dev.leapfrog.sound.FutureSoundRegistry;
import org.jspecify.annotations.Nullable;

/** Same idea as {@link FutureBlockMappings}, for sound events added after 1.21.11. */
public final class FutureSoundMappings implements FullMappings {
    public static final int SENTINEL = 1 << 20;

    private final Mappings delegate;
    private final FutureBlockMappings.Stage stage;

    private FutureSoundMappings(Mappings delegate, FutureBlockMappings.Stage stage) {
        this.delegate = delegate;
        this.stage = stage;
    }

    static Mappings wrap(Mappings current, FutureBlockMappings.Stage stage) {
        return current instanceof FutureSoundMappings ? current : new FutureSoundMappings(current, stage);
    }

    @Override
    public int getNewId(int id) {
        switch (stage) {
            case ENTRY:
                if (Config.futureSounds && FutureSoundRegistry.isFuture(id)) return SENTINEL + id;
                return delegate.getNewId(id);
            case PASS:
                return id >= SENTINEL ? id : delegate.getNewId(id);
            default:
                if (id >= SENTINEL) {
                    int client = FutureSoundRegistry.clientId(id - SENTINEL);
                    return client >= 0 ? client : delegate.getNewId(0);
                }
                return delegate.getNewId(id);
        }
    }

    private FullMappings full() {
        if (delegate instanceof FullMappings f) return f;
        throw new UnsupportedOperationException("Sound mappings have no identifiers");
    }

    @Override public void setNewId(int id, int mappedId) { delegate.setNewId(id, mappedId); }
    @Override public int size() { return delegate.size(); }
    @Override public int mappedSize() { return delegate.mappedSize(); }
    @Override public boolean isIdentity() { return false; }
    @Override public boolean isIntIdIdentity() { return false; }
    @Override public int id(String identifier) { return full().id(identifier); }
    @Override public int mappedId(String mappedIdentifier) { return full().mappedId(mappedIdentifier); }
    @Override public @Nullable Key keyFromId(int id) { return full().keyFromId(id); }
    @Override public @Nullable Key keyFromMappedKey(String mappedIdentifier) { return full().keyFromMappedKey(mappedIdentifier); }
    @Override public @Nullable Key mappedKeyFromMappedId(int mappedId) { return full().mappedKeyFromMappedId(mappedId); }
    @Override
    public @Nullable Key mappedKeyFromKey(String identifier) {
        if (Config.futureSounds && FutureSoundRegistry.isFutureName(identifier)) return null; // keep the 26.x name
        return full().mappedKeyFromKey(identifier);
    }

    @Override
    public @Nullable String mappedIdentifier(String identifier) {
        if (Config.futureSounds && FutureSoundRegistry.isFutureName(identifier)) return null; // "unchanged" for SoundRewriter
        return full().mappedIdentifier(identifier);
    }
    @Override public FullMappings inverse() { return full().inverse(); }
}
